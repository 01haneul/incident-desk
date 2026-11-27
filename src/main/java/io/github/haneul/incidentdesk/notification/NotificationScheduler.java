package io.github.haneul.incidentdesk.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
        name = "incident.notification.scheduling-enabled",
        havingValue = "true"
)
public class NotificationScheduler {

    private final NotificationDispatcher dispatcher;

    public NotificationScheduler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${incident.notification.poll-delay-ms:1000}")
    public void dispatchNextNotification() {
        dispatcher.dispatchOne();
    }
}