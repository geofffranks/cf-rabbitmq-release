(ns io.pivotal.pcf.rabbitmq.config
  (:require [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [validateur.validation :as vdt :refer [validation-set
                                                   presence-of
                                                   inclusion-of
                                                   validate-with-predicate]]
            [io.pivotal.pcf.rabbitmq.constants :refer [management-ui-port]]))

;;
;; Implementation
;;

(def pcf-product-cost {:amount {:usd  0.0}
                       :unit "MONTHLY"})

(defn ^:private present-and-non-empty?
  [v]
  (not (empty? (or v []))))

(def config-validator
  (let [missing-msg "must be present"]
    (validation-set
     ;; CC
     (presence-of :cc_endpoint :message missing-msg)
     ;; PID file location
     (presence-of :pid :message missing-msg)
     ;; logging
     (presence-of [:logging :level] :message missing-msg)
     (inclusion-of [:logging :level] :in #{"debug" "info" "warn" "error" "fatal"})
     ;; UAA authentication
     (presence-of [:uaa_client :username] :message missing-msg)
     (presence-of [:uaa_client :password] :message missing-msg)
     (presence-of [:uaa_client :client_id] :message missing-msg)
     ;; CC/broker authentication
     (presence-of [:service :username] :message missing-msg)
     (presence-of [:service :password] :message missing-msg)
     ;; Broker catalog
     (presence-of [:service :name] :message missing-msg)
     (presence-of [:service :uuid] :message missing-msg)
     (presence-of [:service :plan_uuid] :message missing-msg)
     ;; public management UI route
     (presence-of [:rabbitmq :management_domain] :message missing-msg)
     ;; rabbitmq info
     (presence-of [:rabbitmq :hosts] :message missing-msg)
     (validate-with-predicate [:rabbitmq :hosts] present-and-non-empty? :message "must have at least one entry")
     (presence-of [:rabbitmq :administrator :username] :message missing-msg)
     (presence-of [:rabbitmq :administrator :password] :message missing-msg))))

(def ^{:private true} final-config)

;;
;; API
;;

(defn init!
  [m]
  (alter-var-root #'final-config (constantly m)))

(defn valid?
  "Returns true if provided configuration is valid, false otherwise"
  [m]
  (vdt/valid? config-validator m))

(defn validate
  "Validates provided configuration, returning a map of errors
   (attribute name to a set of error messages)"
  [m]
  (config-validator m))

(defn from-path
  "Loads config from specified local file system path"
  [^String path]
  (yaml/parse-string (slurp path)))

(defn serializable-config
  []
  (dissoc final-config :cf-client))

(defn log-level
  [m]
  (get-in m [:logging :level] "info"))

(defn print-stack-traces?
  [m]
  (true? (get-in m [:logging :print_stack_traces] false)))

(defn ^String pid-path
  [m]
  (get m :pid))

(defn ^String cc-endpoint
  [m]
  (get m :cc_endpoint))

(defn ^String uaa-client-id
  [m]
  (get-in m [:uaa_client :client_id]))

(defn ^String uaa-username
  [m]
  (get-in m [:uaa_client :username]))

(defn ^String uaa-password
  [m]
  (get-in m [:uaa_client :password]))

(defn service-info
  [m]
  (let [svs           (get m :service)
        service-uuid  (get-in m [:service :uuid])
        service-name  (get-in m [:service :name])
        plan-uuid     (get-in m [:service :plan_uuid])
        long-desc     "RabbitMQ is a robust and scalable high-performance multi-protocol messaging broker."
        ;; displayed in Console
        img-url       "data:image/png;base64,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"
        provider-name "Pivotal"]
    {:id          service-uuid
     :name        service-name
     :description long-desc
     :bindable    true
     :requires    []
     :tags        ["rabbitmq" "messaging" "message-queue" "amqp" "stomp" "mqtt" "pivotal"]
     :metadata    {"displayName"         "RabbitMQ"
                   "longDescription"     long-desc
                   "imageUrl"            img-url
                   ;; backwards compatibility
                   "listing"             {"blurb"    long-desc
                                          "imageUrl" img-url}
                   "providerDisplayName" provider-name
                   "documentationUrl"    "http://docs.pivotal.io"
                   "supportUrl"          "https://support.pivotal.io"
                   "provider"            {"name" provider-name}}
     :plans       [{:id plan-uuid
                    :name "Standard"
                    :description "Provides a multi-tenant RabbitMQ cluster"
                    :metadata    {"displayName" "Production"
                                  "costs"       [pcf-product-cost]
                                  "bullets"     ["RabbitMQ 3.5.6" "Multi-tenant"]}}]}))

(defn using-tls?
  ([]
     (using-tls? final-config))
  ([m]
     (not (not (or (get-in m [:rabbitmq :ssl])
                   (get-in m [:rabbitmq :tls]))))))

(defn operator-set-policy-enabled?
  ([]
   (operator-set-policy-enabled? final-config))
  ([m]
   (true? (get-in m [:rabbitmq :operator_set_policy :enabled] false))))

(defn operator-set-policy-name
  ([]
   (operator-set-policy-name final-config))
  ([m]
   (get-in m [:rabbitmq :operator_set_policy :policy_name] "operator_set_policy")))

(defn operator-set-policy-definition
  ([]
    (operator-set-policy-definition final-config))
  ([m]
    (try
      (json/read-str
        (get-in m [:rabbitmq :operator_set_policy :policy_definition])
        :key-fn keyword)
      (catch Exception e
        (.printStackTrace e)))))

(defn operator-set-policy-priority
  ([]
   (operator-set-policy-priority final-config))
  ([m]
   (get-in m [:rabbitmq :operator_set_policy :policy_priority] 50)))

(defn management-domain
  ([]
     (management-domain final-config))
  ([m]
     (get-in m [:rabbitmq :management_domain])))

(defn authenticated?
  ([^String username ^String password]
     (authenticated? final-config username password))
  ([m ^String username ^String password]
     (let [u (get-in m [:service :username])
           p (get-in m [:service :password])]
       (and (= u username)
            (= p password)))))

(defn ^String http-scheme
  ([]
     (http-scheme final-config))
  ([m]
     (if (using-tls? m)
       "https"
       "http")))

(defn ^String amqp-scheme
  ([]
     (amqp-scheme final-config))
  ([m]
     (if (using-tls? m) "amqps" "amqp")))

(defn rabbitmq-administrator
  ([]
     (rabbitmq-administrator final-config))
  ([m]
     (get-in m [:rabbitmq :administrator :username])))

(defn rabbitmq-administrator-password
  ([]
     (rabbitmq-administrator-password final-config))
  ([m]
     (get-in m [:rabbitmq :administrator :password])))

(defn node-hosts
  ([]
     (node-hosts final-config))
  ([m]
    (if-let [dns-host (get-in m [:rabbitmq :dns_host])]
      [dns-host]
      (vec (get-in m [:rabbitmq :hosts])))))

(defn rabbitmq-administrator-uris
  ([]
     (rabbitmq-administrator-uris final-config))
  ([m]
     (mapv
       #(format "http://%s:%d" % management-ui-port)
       (node-hosts m))))
