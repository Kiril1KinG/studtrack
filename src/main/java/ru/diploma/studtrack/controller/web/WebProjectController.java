package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class WebProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;

    @GetMapping
    public String listProjects(Model model) {
        List<Project> projects = projectService.getMyProjects();
        model.addAttribute("projects", projects);
        model.addAttribute("pageTitle", "Мои проекты");
        return "projects/list";
    }

    @GetMapping("/create")
    public String createProjectForm(Model model) {
        model.addAttribute("pageTitle", "Создать проект");
        return "projects/create";
    }

    @PostMapping("/create")
    public String createProject(@RequestParam String name,
                                @RequestParam(required = false) String description) {
        log.info("Создание проекта через веб-форму: name={}, description={}", name, description);
        Project project = projectService.create(name, description);
        return "redirect:/projects/" + project.getId();
    }

    @GetMapping("/{id}")
    public String viewProject(@PathVariable UUID id, Model model) {
        Project project = projectService.findById(id);
        List<Task> tasks = taskService.getTasksByProject(id);
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));

        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("statuses", Task.TaskStatus.values());
        model.addAttribute("priorities", Task.Priority.values());
        model.addAttribute("pageTitle", project.getName());
        return "projects/detail";
    }
}