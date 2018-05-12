package de.endrullis.idea.postfixtemplates.language;

import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import de.endrullis.idea.postfixtemplates.language.psi.CptFile;
import de.endrullis.idea.postfixtemplates.language.psi.CptMapping;
import de.endrullis.idea.postfixtemplates.languages.SupportedLanguages;
import de.endrullis.idea.postfixtemplates.settings.CptApplicationSettings;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.endrullis.idea.postfixtemplates.utils.CollectionUtils._List;

@SuppressWarnings("WeakerAccess")
public class CptUtil {
	public static final String PLUGIN_ID = "de.endrullis.idea.postfixtemplates";

	public static Project findProject(PsiElement element) {
		PsiFile containingFile = element.getContainingFile();
		if (containingFile == null) {
			if (!element.isValid()) {
				return null;
			}
		} else if (!containingFile.isValid()) {
			return null;
		}

		return (containingFile == null ? element : containingFile).getProject();
	}

	public static List<CptMapping> findMappings(Project project, String key) {
		List<CptMapping> result = null;

		Collection<VirtualFile> virtualFiles =
			FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME, CptFileType.INSTANCE,
				GlobalSearchScope.allScope(project));

		for (VirtualFile virtualFile : virtualFiles) {
			CptFile cptFile = (CptFile) PsiManager.getInstance(project).findFile(virtualFile);

			if (cptFile != null) {
				CptMapping[] mappings = PsiTreeUtil.getChildrenOfType(cptFile, CptMapping.class);
				if (mappings != null) {
					for (CptMapping mapping : mappings) {
						if (key.equals(mapping.getMatchingClassName())) {
							if (result == null) {
								result = new ArrayList<>();
							}
							result.add(mapping);
						}
					}
				}
			}
		}
		return result != null ? result : Collections.emptyList();
	}

	public static List<CptMapping> findMappings(Project project) {
		List<CptMapping> result = new ArrayList<>();

		Collection<VirtualFile> virtualFiles =
			FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME, CptFileType.INSTANCE,
				GlobalSearchScope.allScope(project));

		for (VirtualFile virtualFile : virtualFiles) {
			CptFile cptFile = (CptFile) PsiManager.getInstance(project).findFile(virtualFile);
			if (cptFile != null) {
				CptMapping[] mappings = PsiTreeUtil.getChildrenOfType(cptFile, CptMapping.class);
				if (mappings != null) {
					Collections.addAll(result, mappings);
				}
			}
		}
		return result;
	}

	/**
	 * Returns the predefined plugin templates for the given language.
	 *
	 * @param language programming language
	 * @return predefined plugin templates for the given language
	 */
	public static String getDefaultTemplates(String language) {
		InputStream stream = CptUtil.class.getResourceAsStream("defaulttemplates/" + language + ".postfixTemplates");
		return getContent(stream);
	}

	/**
	 * Returns the content of the given file.
	 *
	 * @param file file
	 * @return content of the given file
	 * @throws FileNotFoundException
	 */
	public static String getContent(@NotNull File file) throws FileNotFoundException {
		return getContent(new FileInputStream(file));
	}

	/**
	 * Returns the content of the given input stream.
	 *
	 * @param stream input stream
	 * @return content of the given input stream
	 */
	private static String getContent(@NotNull InputStream stream) {
		StringBuilder sb = new StringBuilder();

		// convert system newlines into UNIX newlines, because IDEA works only with UNIX newlines
		try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line).append("\n");
			}

			return sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return "";
	}

	/**
	 * Returns the path of the CPT plugin settings directory.
	 *
	 * @return path of the CPT plugin settings directory
	 */
	public static File getPluginPath() {
		File path = PluginManager.getPlugin(PluginId.getId(CptUtil.PLUGIN_ID)).getPath();

		if (path.getName().endsWith(".jar")) {
			path = new File(path.getParentFile(), path.getName().substring(0, path.getName().length() - 4));
		}

		return path;
	}

	/**
	 * Returns the path "$CPT_PLUGIN_SETTINGS/templates".
	 *
	 * @return path "$CPT_PLUGIN_SETTINGS/templates"
	 */
	public static File getTemplatesPath() {
		return new File(getPluginPath(), "templates");
	}

	@Deprecated
	public static void createTemplateFile(@NotNull String language, String content) {
		File file = new File(CptUtil.getTemplatesPath(), language + ".postfixTemplates");

		//noinspection ResultOfMethodCallIgnored
		file.getParentFile().mkdirs();

		try (PrintStream out = new PrintStream(file, "UTF-8")) {
			out.println(content);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(language + " template file could not copied to " + file.getAbsolutePath(), e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 not supported", e);
		}
	}

	public static Optional<File> getOldTemplateFile(@NotNull String language) {
		if (SupportedLanguages.supportedLanguageIds.contains(language.toLowerCase())) {
			File file = new File(CptUtil.getTemplatesPath(), language + ".postfixTemplates");

			if (file.exists()) {
				// move file to new position
				val newFile = getTemplateFile(language, "oldUserTemplates");
				
				if (file.renameTo(newFile)) {
					return Optional.of(newFile);
				} else {
					return Optional.empty();
				}
			}

			return Optional.empty();
		} else {
			return Optional.empty();
		}
	}

	public static File getTemplateFile(@NotNull String language, @NotNull String fileName) {
		val path = getTemplateDir(language);

		return new File(path, fileName + ".postfixTemplates");
	}

	@NotNull
	public static File getTemplateDir(@NotNull String language) {
		final File dir = new File(getTemplatesPath(), language);

		if (!dir.exists()) {
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
		}

		return dir;
	}

	public static List<File> getTemplateFiles(@NotNull String language) {
		if (SupportedLanguages.supportedLanguageIds.contains(language.toLowerCase())) {
			// eventually move old templates file to new directory
			getOldTemplateFile(language);

			val filesFromDir = getTemplateFilesFromLanguageDir(language);

			val settings = CptApplicationSettings.getInstance().getPluginSettings();

			val vFiles = settings.getLangName2virtualFile().getOrDefault(language, new ArrayList<>());
			val allFilesFromConfig = vFiles.stream().map(f -> f.file).collect(Collectors.toSet());
			val enabledFilesFromConfig = vFiles.stream().filter(f -> f.enabled).map(f -> new File(f.file)).filter(f -> f.exists()).collect(Collectors.toList());

			val remainingTemplateFilesFromDir = Arrays.stream(filesFromDir).filter(f -> !allFilesFromConfig.contains(f.getAbsolutePath()));

			// templateFilesFromConfig + remainingTemplateFilesFromDir
			return Stream.concat(remainingTemplateFilesFromDir, enabledFilesFromConfig.stream()).collect(Collectors.toList());
		} else {
			return _List();
		}
	}

	public static List<File> getEditableTemplateFiles(@NotNull String language) {
		if (SupportedLanguages.supportedLanguageIds.contains(language.toLowerCase())) {
			// eventually move old templates file to new directory
			getOldTemplateFile(language);

			val filesFromDir = getTemplateFilesFromLanguageDir(language);

			val settings = CptApplicationSettings.getInstance().getPluginSettings();

			val vFiles = settings.getLangName2virtualFile().getOrDefault(language, new ArrayList<>());
			val allFilesFromConfig = vFiles.stream().map(f -> f.file).collect(Collectors.toSet());
			val enabledFilesFromConfig = vFiles.stream().filter(f -> f.enabled && f.url == null).map(f -> new File(f.file)).filter(f -> f.exists()).collect(Collectors.toList());

			val remainingTemplateFilesFromDir = Arrays.stream(filesFromDir).filter(f -> !allFilesFromConfig.contains(f.getAbsolutePath()));

			// templateFilesFromConfig + remainingTemplateFilesFromDir
			return Stream.concat(remainingTemplateFilesFromDir, enabledFilesFromConfig.stream()).collect(Collectors.toList());
		} else {
			return _List();
		}
	}

	@NotNull
	public static File[] getTemplateFilesFromLanguageDir(@NotNull String language) {
		final File[] files = getTemplateDir(language).listFiles(f -> f.getName().endsWith(".postfixTemplates"));

		if (files == null) {
			return new File[0];
		}

		return files;
	}

	@Nullable
	public static CptLang getLanguageOfTemplateFile(@NotNull VirtualFile vFile) {
		/*
		val settings = CptApplicationSettings.getInstance().getPluginSettings();

		val path = getPath(vFile);

		return settings.getFile2langName().get(path);
  	*/

		val name = vFile.getNameWithoutExtension();

		return Optional.ofNullable(
			SupportedLanguages.getCptLang(name)
		).orElseGet(() -> {
			return SupportedLanguages.getCptLang(getAbsoluteVirtualFile(vFile).getParent().getName());
		});
	}

	@NotNull
	public static String getPath(@NotNull VirtualFile vFile) {
		return getAbsoluteVirtualFile(vFile).getPath().replace('\\', '/');
	}

	@NotNull
	public static VirtualFile getAbsoluteVirtualFile(@NotNull VirtualFile vFile) {
		if (vFile instanceof LightVirtualFile) {
			vFile = ((LightVirtualFile) vFile).getOriginalFile();
		}

		return vFile;
	}

	public static void openFileInEditor(@NotNull Project project, @NotNull File file) {
		VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

		if (vFile == null) {
			vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
		}

		assert vFile != null;
		
		openFileInEditor(project, vFile);
	}

	public static void openFileInEditor(@NotNull Project project, @NotNull VirtualFile vFile) {
		// open templates file in an editor
		new OpenFileDescriptor(project, vFile).navigate(true);
	}

	public static Project getActiveProject() {
		DataContext dataContext = DataManager.getInstance().getDataContextFromFocus().getResult();
		return DataKeys.PROJECT.getData(dataContext);
	}

	public static boolean isTemplateFile(@NotNull VirtualFile vFile, @NotNull String language) {
		val settings = CptApplicationSettings.getInstance().getPluginSettings();

		val path = getPath(vFile);

		return settings.getFile2langName().get(path).equals(language);
	}

	@NotNull
	public static String getTemplateSuffix() {
		return CptApplicationSettings.getInstance().getPluginSettings().getTemplateSuffix();
	}

}
