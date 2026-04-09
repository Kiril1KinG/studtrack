package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    //TODO Временная заглушка для текущего пользователя (пока нет Security)
    public UUID getCurrentUserId() {
        return userRepository.findAll().stream()
                .findFirst()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("В базе данных нет ни одного пользователя"));
    }

    public User getCurrentUser() {
        return findById(getCurrentUserId());
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь", id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь с email " + email + " не найден"));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User create(User user) {
        if (existsByEmail(user.getEmail())) {
            throw new AlreadyExistsException("Пользователь", "email", user.getEmail());
        }
        return userRepository.save(user);
    }

    @Transactional
    public User update(UUID id, User updatedUser) {
        User existing = findById(id);
        existing.setFullName(updatedUser.getFullName());
        //TODO Email и password пока не меняем
        return userRepository.save(existing);
    }
}