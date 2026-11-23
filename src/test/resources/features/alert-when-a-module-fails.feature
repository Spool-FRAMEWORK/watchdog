Feature: The watchdog alerts an external webhook when a module fails or comes back

  The watchdog sends an alert to the webhook in WATCHDOG_ALERT_WEBHOOK_URL when a module goes silent, comes back,
  is retired after a long silence, or reports a different status than before. The alert is a JSON object whose text
  a person can read and that a Slack webhook takes as it is, with the event, the module and the details as fields.
  The log keeps every event as before. An alert that cannot be sent never stops the watchdog.

  Background:
    Given a webhook that records the alerts it receives
    And the watchdog alerts that webhook, with a module timeout of 1s and a zombie timeout of 3s

  @DEV-30
  Scenario: A module that stops beating is alerted as degraded
    Given the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" stops beating for 2s
    Then the webhook receives a "MODULE_DEGRADED" alert for "orders-crawler"
    And the text of the alert names "orders-crawler"

  @DEV-30
  Scenario: A degraded module that beats again is alerted as recovered, with the time it was down
    Given the module "orders-crawler" beats as HEALTHY
    And the module "orders-crawler" stops beating for 2s
    When the module "orders-crawler" beats as HEALTHY
    Then the webhook receives a "MODULE_RECOVERED" alert for "orders-crawler"
    And the alert says the module was down for at least 1s

  @DEV-30
  Scenario: A module silent for longer than the zombie timeout is alerted as finished
    Given the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" stops beating for 5s
    Then the webhook receives a "MODULE_FINISHED" alert for "orders-crawler"

  @DEV-30
  Scenario: A module that reports it is unhealthy is alerted with the pieces that fail
    Given the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" beats as UNHEALTHY with the piece "lake" UNHEALTHY because "The folder /lake does not exist"
    Then the webhook receives a "MODULE_STATUS_CHANGED" alert for "orders-crawler" from HEALTHY to UNHEALTHY
    And the alert names the piece "lake" and the reason "The folder /lake does not exist"

  @DEV-30
  Scenario: A module that reports it is healthy again is alerted
    Given the module "orders-crawler" beats as UNHEALTHY with the piece "lake" UNHEALTHY because "The folder /lake does not exist"
    When the module "orders-crawler" beats as HEALTHY
    Then the webhook receives a "MODULE_STATUS_CHANGED" alert for "orders-crawler" from UNHEALTHY to HEALTHY

  @DEV-30
  Scenario: A module that keeps reporting the same status is alerted once
    Given the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" beats as UNHEALTHY with the piece "lake" UNHEALTHY because "The folder /lake does not exist"
    And the module "orders-crawler" beats as UNHEALTHY with the piece "lake" UNHEALTHY because "The folder /lake does not exist"
    Then the webhook receives 1 "MODULE_STATUS_CHANGED" alert for "orders-crawler"

  @DEV-30
  Scenario: A webhook that fails does not stop the watchdog
    Given the webhook answers 500 to every alert
    And the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" stops beating for 2s
    And the module "orders-crawler" beats as HEALTHY
    Then the health of the watchdog lists the module "orders-crawler" as HEALTHY

  @DEV-30
  Scenario: Without a webhook, the watchdog sends no alert and keeps working
    Given the watchdog has no alert webhook
    And the module "orders-crawler" beats as HEALTHY
    When the module "orders-crawler" stops beating for 2s
    Then the webhook receives no alert
    And the health of the watchdog lists the module "orders-crawler" as DEGRADED
