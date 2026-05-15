package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.Notification;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    long countByRecipientIdAndReadFalse(UUID recipientId);

    List<Notification> findTop15ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<Notification> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<Notification> findByRecipientIdAndReadFalse(UUID recipientId);
}
