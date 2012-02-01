(ns backend.test.core
  (:require [somnium.congomongo :as mongo]
            [workers.server :as server]
            [workers.client :as client]
            [lamina.core :as l])
  (:use [backend.core] :reload)
  (:use clojure.test
        robert.hooke)
  (:import [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit ExecutionResult]
           [es.uvigo.ei.sing.dare.domain IBackend Maybe ExecutionTimeExceededException ExecutionFailedException]
           backend.core.Backend))

(def *backend*)

(def input-causing-error (str "input-causing-error" (rand-int 100)))

(defn send-exception-on-input-causing-error [f robot inputs]
  (when (some #{input-causing-error} inputs)
    (throw (Exception. "Stubbed error")))
  (f robot inputs))

(add-hook #'server/execute-robot send-exception-on-input-causing-error)

(def input-causing-reduced-timeout (str "reduced-timeout" (rand-int 100)))

(defn cause-reduced-timeout [f {:keys [inputs] :as request}]
  (let [pred #{input-causing-reduced-timeout}]
    (if-not (some pred inputs)
      (f request)
      (let [[name callable] (f (assoc request :inputs (remove pred inputs)))]
        [name (fn [& args]
                (binding [server/*time-allowed-for-execution-ms* 1]
                  (apply callable args)))]))))

(add-hook #'server/callable-execution cause-reduced-timeout)

(defmacro with-server [server-bindings & body]
  {:pre [(vector? server-bindings) (even? (count server-bindings))]}
  (cond
   (= 0 (count server-bindings)) `(do ~@body)
   :else `(let ~(subvec server-bindings 0 2)
            (try
              (with-server ~(subvec server-bindings 2) ~@body)
              (finally
               (server/shutdown ~(server-bindings 0) :complete-exit false))))))

(def create-server (partial server/local-setup :test))

(defn erase-all-data! []
  (doseq [coll [:periodical-executions :robots :executions]]
    (mongo/destroy! coll {})))

(defn backend-fixture [f]
  (with-server [server (create-server 3333)]
    (binding [*fast-testing-polling-mode* true
              *minimal-allowed-execution-period-ms* (* 2 1000)
              *time-allowed-for-periodical-execution-ms* 2000
              *print-background-task* false
              client/*check-healthy-interval-ms* 300]
      (binding [*backend* (create-backend :db :test)]
        (on *backend*
          (erase-all-data!))
        (on *backend*
            (f)
            (.close *backend*))))))

(use-fixtures :once backend-fixture)

(deftest can-create-entities
  (let [example-robot (Robot/createFromMinilanguage "url")]
    (is (= "url" (.getTransformerInMinilanguage example-robot)))))

(defmacro equal-values-on [fns a b]
  `(are [~'x ~'y] (= ~'x ~'y)
        ~@(mapcat (fn [f] [`(~f ~a) `(~f ~b)]) fns)))

(defn robots-equivalent [r1 r2]
  (equal-values-on [.getCode .getTransformerInMinilanguage .getCreationTime] r1 r2))

(deftest a-robot-can-be-saved-and-retrieved-later
  (let [example-robot (Robot/createFromMinilanguage "url")
        _ (.save *backend* example-robot)
        robot-retrieved (.find *backend* (.getCode example-robot))]
    (is ((complement nil?) robot-retrieved))
    (robots-equivalent example-robot robot-retrieved)))

(deftest finding-a-non-existent-robot-returns-nil
  (is (nil? (.find *backend* (new-unique-code)))))

(deftest the-robot-of-a-periodical-execution-must-be-saved
  (let [robot (Robot/createFromMinilanguage "url")
        period (ExecutionPeriod. 1 ExecutionPeriod$Unit/DAYS)
        periodical-execution (.createPeriodical robot
                                                period ["http://www.esei.uvigo.es"])]
    (is (thrown? java.lang.AssertionError (.save *backend* periodical-execution)))))

(deftest submiting-robot-with-execution
  (let [robot (Robot/createFromMinilanguage "url")
        submit-execution-with-inputs #(.submitExecution *backend* robot %&)
        submit-execution (partial submit-execution-with-inputs
                                  "http://www.esei.uvigo.es")
        code (submit-execution)
        robot-retrieved (.find *backend* (.getCode robot))
        wait-for-result #(l/wait-for-result
                          (poll-for-execution-result *backend* %) 8000)]
    (testing "saves the provided robot"
      (robots-equivalent robot robot-retrieved))
    (testing "eventually creates a result"
      (let [execution-result (wait-for-result code)]
        (is ((complement nil?) execution-result))
        (is (= code (.getCode execution-result)))
        (is (= 1 (count (.getInputs execution-result))))
        (is (< 0 (count (.getResultLines execution-result))))))
    (testing "if the execution timeouts, appropriate error is returned"
      (binding [*time-allowed-for-execution-ms* 1]
        (let [code (submit-execution)]
          (is (thrown? ExecutionTimeExceededException
                       (.retrieveExecution *backend* code))))))
    (testing "if the execution fails, appropiate error is returned"
      (let [code (submit-execution-with-inputs input-causing-error)]
        (is (thrown? ExecutionFailedException (wait-for-result code)))))
    (testing "If the execution timeouts in the server, appropiate error is returned"
      (let [code (submit-execution input-causing-reduced-timeout)]
        (is (thrown? ExecutionTimeExceededException (wait-for-result code)))))))

(deftest retrieving-a-not-existent-execution-returns-nil
  (is (nil? (.retrieveExecution *backend* (new-unique-code)))))

(deftest execution-result-at-initial-state-implies-none-is-returned
  (let [robot (Robot/createFromMinilanguage "url")
        code (insert-execution-at-initial-state robot [])
        ^Maybe maybe (.retrieveExecution *backend* code)]
    (is (.isNone maybe))))

(deftest the-robot-must-exist-when-providing-the-code
  (is (thrown? java.lang.AssertionError
               (.submitExecutionForExistentRobot *backend* (new-unique-code) ["one" "two"]))))

(deftest execution-result-with-results-fullfiled-implies-result-returned
  (let [robot (Robot/createFromMinilanguage "url")
        code (insert-execution-at-initial-state robot [])
        result-lines ["one" "two"]
        _ (mongo/update! :executions {:_id code} {:$set {:resultLines result-lines
                                                         :executionTimeMilliseconds 1000}})
        ^Maybe maybe (.retrieveExecution *backend* code)]
    (is (.hasValue maybe))
    (let [execution-result (.getValue maybe)]
      (are [x y] (= x y)
           (.getCode execution-result) code
           (.getOptionalRobotCode execution-result) (.getCode robot)
           (.getResultLines execution-result) result-lines))))

(deftest finding-a-non-existent-periodical-execution-returns-nil
  (is (nil? (.findPeriodicalExecution *backend* (new-unique-code)))))

(deftest a-periodical-execution-can-be-saved-and-retrieved
  (let [robot (Robot/createFromMinilanguage "url")
        _ (.save *backend* robot)
        period (ExecutionPeriod. 1 ExecutionPeriod$Unit/DAYS)
        periodical-execution (.createPeriodical robot
                                                period ["http://www.esei.uvigo.es"])
        code (.getCode periodical-execution)
        retrieve-periodical (fn [] (.findPeriodicalExecution *backend* code))
        erase-previous-periodical-result
        (fn [] (mongo/update! :periodical-executions {:_id code}
                             {:$set {:last-execution nil
                                     :next-execution-ms (now-ms)}}
                             :upsert false))
        assert-an-execution-eventually-exists
        (fn []
          (let [_ (l/wait-for-result (poll-for-next-periodical-execution-result
                                      *backend* code) 8000)
                updated-periodical (retrieve-periodical)
                last-execution (.getLastExecutionResult updated-periodical)]
            (is ((complement nil?) last-execution))
            (equal-values-on [.getCreationTime .getInputs .getRobotCode]
                             periodical-execution updated-periodical)))]
    (testing "without last execution"
      (.save *backend* periodical-execution)
      (equal-values-on [.getCreationTime .getInputs .getRobotCode .getLastExecutionResult]
                       periodical-execution (retrieve-periodical)))
    (testing "eventually the periodical execution is scheduled and executed after being created"
      (assert-an-execution-eventually-exists))
    (testing "executions scheduled but not send are cleaned so they can be scheduled again"
      (erase-previous-periodical-result)
      (mark-as-scheduled code)
      (assert-an-execution-eventually-exists))))

(deftest robots-periodical-executions-and-result-executions-can-be-deleted
  (let [robot (Robot/createFromMinilanguage "url")
        _ (.save *backend* robot)
        period (ExecutionPeriod. 1 ExecutionPeriod$Unit/DAYS)
        inputs ["http://www.esei.uvigo.es"]
        save-periodical
        (fn []
          (let [result (.createPeriodical robot period inputs)]
            (.save *backend* result)
            (.getCode result)))
        save-execution
        (fn []
          (.submitExecutionForExistentRobot *backend* (.getCode robot) inputs))
        retrieve-execution (fn [code] (.retrieveExecution *backend* code))
        retrieve-periodical (fn [code] (.findPeriodicalExecution *backend* code))
        retrieve-robot (fn [code] (.find *backend* code))]
    (testing "Deleting an execution removes it from storage"
      (let [code (save-execution)]
        (is ((complement nil?) (retrieve-execution code)))
        (.deleteExecution *backend* code)
        (is (nil? (retrieve-execution code)))))
    (testing "Deleting a periodical execution removes it from storage"
      (let [code (save-periodical)]
        (is ((complement nil?) (retrieve-periodical code)))
        (.deletePeriodical *backend* code)
        (is (nil? (retrieve-periodical code)))))
    (testing "Deleting the robot makes it unavailable along with the associated robots and periodical executions"
      (let [robot-code (.getCode robot)
            execution-code (save-execution)
            periodical-code (save-periodical)]
        (.deleteRobot *backend* robot-code)
        (is (nil? (retrieve-robot robot-code)))
        (is (nil? (retrieve-execution execution-code)))
        (is (nil? (retrieve-periodical periodical-code)))))))

(deftest can-find-new-workers
  (letfn [(count-alive-workers [] (client/count-alive-workers (:workers *backend*)))
          (wait-for-check-healthy [factor]
            (Thread/sleep (-> client/*check-healthy-interval-ms*
                              (* factor)
                              (int))))]
    (is (= 1 (count-alive-workers)))
    (with-server [new-server (create-server 44444)]
      (wait-for-check-healthy 5/2)
      (is (= 2 (count-alive-workers))))
    (wait-for-check-healthy 5/2)
    (is (= 1 (count-alive-workers)))
    (with-server [new-server (create-server 44444)]
      (wait-for-check-healthy 4)
      (is (= 2 (count-alive-workers))))))

(deftest Backend-is-an-implementation-of-IBackend
  (is (instance? IBackend *backend*)))
