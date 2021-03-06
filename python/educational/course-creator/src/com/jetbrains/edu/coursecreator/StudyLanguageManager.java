package com.jetbrains.edu.coursecreator;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StudyLanguageManager {
  LanguageExtension<StudyLanguageManager> INSTANCE = new LanguageExtension<StudyLanguageManager>("Edu.StudyLanguageManager");

  @Nullable
  String getDefaultTaskFileExtension();

  @Nullable
  FileTemplate getTaskFileTemplateForExtension(@NotNull final Project project, String extension);

  @Nullable
  FileTemplate getTestsTemplate(@NotNull final Project project);
}
