package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.UserRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService minioStorageService;

    public UUID getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findByEmail(email).getId();
    }

    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findByEmail(email);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь", id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User register(String email, String password, String fullName, User.Role role) {
        if (existsByEmail(email)) {
            throw new AlreadyExistsException("Пользователь", "email", email);
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role != null ? role : User.Role.STUDENT)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User update(UUID id, User updatedUser) {
        User existing = findById(id);
        existing.setFullName(updatedUser.getFullName());
        return userRepository.save(existing);
    }

    @Transactional
    public void changePassword(UUID id, String oldPassword, String newPassword) {
        User user = findById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateAvatar(UUID id, MultipartFile avatarFile) {
        User user = findById(id);
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new IllegalArgumentException("Файл аватара не выбран");
        }
        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Можно загрузить только изображение");
        }

        String extension = extractExtension(avatarFile.getOriginalFilename());
        String newKey = "avatars/" + user.getId() + "/" + UUID.randomUUID() + extension;
        try {
            minioStorageService.upload(newKey, avatarFile.getInputStream(), contentType, avatarFile.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить аватар", e);
        }

        String oldKey = user.getAvatarKey();
        user.setAvatarKey(newKey);
        user.setAvatarContentType(contentType);
        userRepository.save(user);

        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            minioStorageService.delete(oldKey);
        }
    }

    public String getAvatarUrl(User user) {
        if (user == null || user.getAvatarKey() == null || user.getAvatarKey().isBlank()) {
            return null;
        }
        return minioStorageService.getPresignedDownloadUrl(user.getAvatarKey(), Duration.ofMinutes(30));
    }

    @Transactional
    public void deleteAvatar(UUID id) {
        User user = findById(id);
        String oldKey = user.getAvatarKey();
        if (oldKey == null || oldKey.isBlank()) {
            return;
        }
        user.setAvatarKey(null);
        user.setAvatarContentType(null);
        userRepository.save(user);
        minioStorageService.delete(oldKey);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot);
    }
}