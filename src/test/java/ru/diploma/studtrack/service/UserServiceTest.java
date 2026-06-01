package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private MultipartFile multipartFile;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, minioStorageService);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void registerShouldCreateUserWithNormalizedNameAndEncodedPassword() {
        when(userRepository.existsByEmail("new@test")).thenReturn(false);
        when(passwordEncoder.encode("abc12345")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.register("new@test", "abc12345", " Иванов ", " Иван ", " ");

        assertEquals("new@test", created.getEmail());
        assertEquals("encoded", created.getPassword());
        assertEquals("Иванов", created.getLastName());
        assertEquals("Иван", created.getFirstName());
        assertNull(created.getPatronymic());
        assertEquals("Иванов Иван", created.getFullName());
    }

    @Test
    void registerShouldThrowWhenEmailExists() {
        when(userRepository.existsByEmail("taken@test")).thenReturn(true);

        assertThrows(AlreadyExistsException.class,
                () -> service.register("taken@test", "abc12345", "Иванов", "Иван", null));
    }

    @Test
    void registerShouldThrowWhenPasswordTooWeak() {
        when(userRepository.existsByEmail("new@test")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.register("new@test", "abcdefg", "Иванов", "Иван", null));
    }

    @Test
    void updateShouldRefreshNamePartsAndFullName() {
        UUID id = UUID.randomUUID();
        User existing = User.builder().id(id).email("u@test").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.update(id, User.builder()
                .lastName("Петров")
                .firstName("Петр")
                .patronymic("Петрович")
                .build());

        assertEquals("Петров Петр Петрович", result.getFullName());
        assertEquals("Петрович", result.getPatronymic());
    }

    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).password("encoded").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.changePassword(id, "wrong", "new12345"));
    }

    @Test
    void changePasswordShouldPersistEncodedPassword() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).password("old").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "old")).thenReturn(true);
        when(passwordEncoder.encode("new12345")).thenReturn("newEncoded");

        service.changePassword(id, "oldPass", "new12345");

        assertEquals("newEncoded", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatarShouldUploadAndDeletePreviousFile() throws IOException {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).avatarKey("avatars/old.png").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getContentType()).thenReturn("image/png");
        when(multipartFile.getOriginalFilename()).thenReturn("avatar.png");
        when(multipartFile.getSize()).thenReturn(4L);
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        service.updateAvatar(id, multipartFile);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).upload(keyCaptor.capture(), any(), eq("image/png"), eq(4L));
        String newKey = keyCaptor.getValue();
        assertEquals(newKey, user.getAvatarKey());
        verify(minioStorageService).delete("avatars/old.png");
    }

    @Test
    void getAvatarUrlShouldReturnNullForEmptyAvatar() {
        User user = User.builder().avatarKey(" ").build();
        assertNull(service.getAvatarUrl(user));
    }

    @Test
    void getAvatarUrlShouldReturnPresignedUrl() {
        User user = User.builder().avatarKey("avatars/new.png").build();
        when(minioStorageService.getPresignedDownloadUrl(eq("avatars/new.png"), any(Duration.class))).thenReturn("url");

        assertEquals("url", service.getAvatarUrl(user));
    }

    @Test
    void deleteAvatarShouldSkipWhenNoAvatar() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).avatarKey(null).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        service.deleteAvatar(id);

        verify(userRepository, never()).save(any());
        verify(minioStorageService, never()).delete(any());
    }

    @Test
    void deleteAvatarShouldClearFieldsAndDeleteFile() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).avatarKey("avatars/new.png").avatarContentType("image/png").build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        service.deleteAvatar(id);

        assertNull(user.getAvatarKey());
        assertNull(user.getAvatarContentType());
        verify(userRepository).save(user);
        verify(minioStorageService).delete("avatars/new.png");
    }

    @Test
    void getCurrentUserAndIdShouldUseSecurityContextEmail() {
        User user = User.builder().id(UUID.randomUUID()).email("me@test").build();
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("me@test");
        when(userRepository.findByEmail("me@test")).thenReturn(Optional.of(user));

        assertEquals(user, service.getCurrentUser());
        assertEquals(user.getId(), service.getCurrentUserId());
    }
}

