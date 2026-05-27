package ru.diploma.studtrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "patronymic")
    private String patronymic;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "avatar_content_type")
    private String avatarContentType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "owner")
    private Set<Project> ownedProjects = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<ProjectMember> memberships = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<TaskAssignee> taskAssignments = new HashSet<>();

    @OneToMany(mappedBy = "author")
    private Set<Comment> comments = new HashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<TaskReviewer> reviewAssignments = new HashSet<>();

    @OneToMany(mappedBy = "initiator")
    private Set<TaskReviewRound> initiatedRounds = new HashSet<>();

    @OneToMany(mappedBy = "author")
    private Set<ChangeRequest> authoredChangeRequests = new HashSet<>();

    public String getFullName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        StringJoiner joiner = new StringJoiner(" ");
        if (lastName != null && !lastName.isBlank()) {
            joiner.add(lastName.trim());
        }
        if (firstName != null && !firstName.isBlank()) {
            joiner.add(firstName.trim());
        }
        if (patronymic != null && !patronymic.isBlank()) {
            joiner.add(patronymic.trim());
        }
        return joiner.toString();
    }
}