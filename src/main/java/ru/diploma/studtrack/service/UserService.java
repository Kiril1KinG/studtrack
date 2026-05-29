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
/**
 * Управляет пользователями, их профилем, паролями и аватарами.
 */
public class UserService {

    /**
     * Репозиторий пользователей.
     */
    private final UserRepository userRepository;
    /**
     * Кодировщик паролей.
     */
    private final PasswordEncoder passwordEncoder;
    /**
     * Сервис хранения файлов в MinIO.
     */
    private final MinioStorageService minioStorageService;

    /**
     * Возвращает идентификатор текущего аутентифицированного пользователя.
     *
     * @return идентификатор пользователя
     */
    public UUID getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findByEmail(email).getId();
    }

    /**
     * Возвращает текущего аутентифицированного пользователя.
     *
     * @return текущий пользователь
     */
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return findByEmail(email);
    }

    /**
     * Возвращает пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return пользователь
     */
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь", id));
    }

    /**
     * Возвращает пользователя по email.
     *
     * @param email email пользователя
     * @return пользователь
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
    }

    /**
     * Возвращает всех пользователей.
     *
     * @return список пользователей
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Проверяет существование пользователя по email.
     *
     * @param email email пользователя
     * @return true, если пользователь существует
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    /**
     * Регистрирует нового пользователя.
     *
     * @param email email
     * @param password пароль
     * @param lastName фамилия
     * @param firstName имя
     * @param patronymic отчество
     * @return созданный пользователь
     */
    public User register(String email,
                         String password,
                         String lastName,
                         String firstName,
                         String patronymic) {
        if (existsByEmail(email)) {
            throw new AlreadyExistsException("Пользователь", "email", email);
        }
        validatePasswordComplexity(password);
        String normalizedLastName = normalizeNamePart(lastName, true, "Фамилия");
        String normalizedFirstName = normalizeNamePart(firstName, true, "Имя");
        String normalizedPatronymic = normalizeNamePart(patronymic, false, "Отчество");
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .lastName(normalizedLastName)
                .firstName(normalizedFirstName)
                .patronymic(normalizedPatronymic)
                .fullName(buildFullName(normalizedLastName, normalizedFirstName, normalizedPatronymic))
                .build();
        return userRepository.save(user);
    }

    @Transactional
    /**
     * Обновляет ФИО пользователя.
     *
     * @param id идентификатор пользователя
     * @param updatedUser новые данные
     * @return обновлённый пользователь
     */
    public User update(UUID id, User updatedUser) {
        User existing = findById(id);
        String normalizedLastName = normalizeNamePart(updatedUser.getLastName(), true, "Фамилия");
        String normalizedFirstName = normalizeNamePart(updatedUser.getFirstName(), true, "Имя");
        String normalizedPatronymic = normalizeNamePart(updatedUser.getPatronymic(), false, "Отчество");
        existing.setLastName(normalizedLastName);
        existing.setFirstName(normalizedFirstName);
        existing.setPatronymic(normalizedPatronymic);
        existing.setFullName(buildFullName(normalizedLastName, normalizedFirstName, normalizedPatronymic));
        return userRepository.save(existing);
    }

    @Transactional
    /**
     * Изменяет пароль пользователя.
     *
     * @param id идентификатор пользователя
     * @param oldPassword текущий пароль
     * @param newPassword новый пароль
     */
    public void changePassword(UUID id, String oldPassword, String newPassword) {
        User user = findById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    /**
     * Обновляет аватар пользователя.
     *
     * @param id идентификатор пользователя
     * @param avatarFile файл аватара
     */
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

    /**
     * Возвращает временный URL аватара пользователя.
     *
     * @param user пользователь
     * @return URL аватара или null
     */
    public String getAvatarUrl(User user) {
        if (user == null || user.getAvatarKey() == null || user.getAvatarKey().isBlank()) {
            return null;
        }
        return minioStorageService.getPresignedDownloadUrl(user.getAvatarKey(), Duration.ofMinutes(30));
    }

    @Transactional
    /**
     * Удаляет аватар пользователя.
     *
     * @param id идентификатор пользователя
     */
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

    /**
     * Извлекает расширение имени файла.
     *
     * @param fileName имя файла
     * @return расширение с точкой или пустая строка
     */
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

    /**
     * Нормализует часть ФИО и валидирует обязательность поля.
     *
     * @param value исходное значение
     * @param required флаг обязательности
     * @param fieldLabel название поля для сообщения об ошибке
     * @return нормализованное значение или null
     */
    private String normalizeNamePart(String value, boolean required, String fieldLabel) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " обязательно для заполнения");
        }
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * Формирует полное имя из частей ФИО.
     *
     * @param lastName фамилия
     * @param firstName имя
     * @param patronymic отчество
     * @return полное имя
     */
    private String buildFullName(String lastName, String firstName, String patronymic) {
        StringBuilder builder = new StringBuilder();
        builder.append(lastName).append(" ").append(firstName);
        if (patronymic != null && !patronymic.isBlank()) {
            builder.append(" ").append(patronymic);
        }
        return builder.toString();
    }

    /**
     * Проверяет базовую сложность пароля.
     *
     * @param password пароль
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Пароль должен быть не короче 8 символов и содержать буквы и цифры");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return;
            }
        }
        throw new IllegalArgumentException("Пароль должен быть не короче 8 символов и содержать буквы и цифры");
    }
}