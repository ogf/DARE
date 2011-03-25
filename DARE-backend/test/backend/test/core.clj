(ns backend.test.core
  (:require [somnium.congomongo :as mongo])
  (:use [backend.core] :reload)
  (:use [clojure.test])
  (:import [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit
            ExecutionResult ExecutionResult$Type]
           [es.uvigo.ei.sing.dare.domain IBackend Maybe]
           backend.core.Backend))

(def *backend*)

(defn backend-fixture [f]
  (binding [*backend* (create-backend :db :test)]
    (on *backend*
        (f))))

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
                                                period ["http://www.twitter.com"])]
    (is (thrown? java.lang.AssertionError (.save *backend* periodical-execution)))))

(deftest submiting-execution-saves-the-provided-robot
  (let [robot (Robot/createFromMinilanguage "url")
        code (.submitExecution *backend* robot [])
        robot-retrieved (.find *backend* (.getCode robot))]
    (robots-equivalent robot robot-retrieved)))

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
           (.getType execution-result) ExecutionResult$Type/ROBOT
           (.getCreatedFromCode execution-result) (.getCode robot)
           (.getResultLines execution-result) result-lines))))

(deftest finding-a-non-existent-periodical-execution-returns-nil
  (is (nil? (.findPeriodicalExecution *backend* (new-unique-code)))))

(deftest a-periodical-execution-can-be-saved-and-retrieved
  (let [robot (Robot/createFromMinilanguage "url")
        _ (.save *backend* robot)
        period (ExecutionPeriod. 1 ExecutionPeriod$Unit/DAYS)
        periodical-execution (.createPeriodical robot
                                                period ["http://www.twitter.com"])
        _ (.save *backend* periodical-execution)
        retrieved (.findPeriodicalExecution *backend* (.getCode periodical-execution))]
    (equal-values-on [.getCreationTime .getInputs #(.getRobotCode %)]
                     retrieved periodical-execution)))

(deftest Backend-is-an-implementation-of-IBackend
  (is (instance? IBackend (create-backend :db :test))))
