(defproject deployer "0.1.0-SNAPSHOT"
  :description "FIXME Pallet project for deployer"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.cloudhoist/pallet "0.7.0-beta.2"]
                 [org.cloudhoist/pallet-jclouds "1.4.0-SNAPSHOT" ]
                 [org.cloudhoist/automated-admin-user "0.6.0"]
                 [org.cloudhoist/ruby "0.7.0-SNAPSHOT"]
                 [org.cloudhoist/rubygems "0.7.0-SNAPSHOT"]
                 [org.cloudhoist/java "0.6.0-SNAPSHOT"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [ch.qos.logback/logback-core "1.0.0"]
                 [ch.qos.logback/logback-classic "1.0.0"]
                 [org.jclouds.provider/aws-ec2 "1.4.0"]
                 [org.jclouds.provider/aws-s3 "1.4.0"]]
  ;;:Local-repo-classpath true
  :repositories
  {"sonatype-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"
   "sonatype" "https://oss.sonatype.org/content/repositories/releases/"})
