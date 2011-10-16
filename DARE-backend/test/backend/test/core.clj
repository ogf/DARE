(ns backend.test.core
  (:require [somnium.congomongo :as mongo]
            [workers.server :as server]
            [workers.client :as client]
            [lamina.core :as l])
  (:use [backend.core] :reload)
  (:use [clojure.test])
  (:import [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit ExecutionResult]
           [es.uvigo.ei.sing.dare.domain IBackend Maybe]
           backend.core.Backend))

(def *backend*)

(defmacro with-server [server-bindings & body]
  {:pre [(vector? server-bindings) (even? (count server-bindings))]}
  (cond
   (= 0 (count server-bindings)) `(do ~@body)
   :else `(let ~(subvec server-bindings 0 2)
            (try
              (with-server ~(subvec server-bindings 2) ~@body)
              (finally
               (server/shutdown ~(server-bindings 0)))))))

(def create-server (partial server/local-setup :test))

(defn backend-fixture [f]
  (with-server [server (create-server 3333)]
    (binding [*polling-interval-for-new-workers* 1000]
      (binding [*backend* (create-backend :db :test)
                client/*check-healthy-interval-ms* 100]
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
        code (.submitExecution *backend* robot ["http://www.esei.uvigo.es"])
        robot-retrieved (.find *backend* (.getCode robot))]
    (testing "saves the provided robot"
      (robots-equivalent robot robot-retrieved))
    (testing "eventually creates a result"
      (let [execution-result (l/wait-for-result
                              (poll-for-execution-result *backend* code) 8000)]
        (is ((complement nil?) execution-result))
        (is (= code (.getCode execution-result)))
        (is (< 0 (count (.getResultLines execution-result))))))))

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

(defn save-last-execution-on [periodical-execution last-execution]
  (on *backend*
      (mongo/update! :periodical-executions {:_id (.getCode periodical-execution)}
                     {:$set {:lastExecution last-execution}})))

(deftest a-periodical-execution-can-be-saved-and-retrieved
  (let [robot (Robot/createFromMinilanguage "url")
        _ (.save *backend* robot)
        period (ExecutionPeriod. 1 ExecutionPeriod$Unit/DAYS)
        periodical-execution (.createPeriodical robot
                                                period ["http://www.esei.uvigo.es"])
        retrieve-periodical (fn [] (.findPeriodicalExecution *backend*
                                                            (.getCode periodical-execution)))]
    (testing "without last execution"
      (.save *backend* periodical-execution)
      (equal-values-on [.getCreationTime .getInputs .getRobotCode .getLastExecutionResult]
                       periodical-execution (retrieve-periodical)))
    (testing "with last execution added"
      (let [new-execution-result-map {:_id (new-unique-code)
                                      :creationTime (System/currentTimeMillis)
                                      :executionTimeMilliseconds 1000
                                      :resultLines ["example1" "example2"]}
            _ (save-last-execution-on periodical-execution new-execution-result-map)
            updated-periodical (retrieve-periodical)
            last-execution (.getLastExecutionResult updated-periodical)]
        (is ((complement nil?) last-execution))
        (equal-values-on [.getCreationTime .getInputs .getRobotCode]
                         periodical-execution updated-periodical)
        (are [key-for-map value-on-last-execution] (= (key-for-map new-execution-result-map)
                                                      value-on-last-execution)

             :executionTimeMilliseconds (.getExecutionTimeMilliseconds last-execution)
             :resultLines (.getResultLines last-execution))))))

(deftest can-find-new-workers
  (letfn [(count-alive-workers [] (client/count-alive-workers (:workers *backend*)))]
    (is (= 1 (count-alive-workers)))
    (with-server [new-server (create-server 44444)]
      (Thread/sleep 500)
      (is (= 2 (count-alive-workers))))
    (Thread/sleep 700)
    (is (= 1 (count-alive-workers)))
    (with-server [new-server (create-server 44444)]
      (Thread/sleep 500)
      (is (= 2 (count-alive-workers))))))

(deftest Backend-is-an-implementation-of-IBackend
  (is (instance? IBackend *backend*)))
