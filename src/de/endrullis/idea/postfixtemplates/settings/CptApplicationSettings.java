package de.endrullis.idea.postfixtemplates.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import de.endrullis.idea.postfixtemplates.language.CptLang;
import de.endrullis.idea.postfixtemplates.language.CptUtil;
import lombok.Getter;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

@State(
	name = "CustomPostfixTemplatesApplicationSettings",
	storages = @Storage("customPostfixTemplates.xml")
)
public class CptApplicationSettings implements PersistentStateComponent<CptApplicationSettings.State>, CptPluginSettings.Holder {

	@Getter
	private State state = new State();

	public CptApplicationSettings() {
	}

	@NotNull
	public static CptApplicationSettings getInstance() {
		return ServiceManager.getService(CptApplicationSettings.class);
	}

	@Override
	public void loadState(@NotNull State state) {
		XmlSerializerUtil.copyBean(state, this.state);
	}

	@Override
	public void setPluginSettings(@NotNull CptPluginSettings settings) {
		state.pluginSettings = settings;

		settingsChanged();
	}

	/**
	 * This method is called after the user changed some settings and saved them.
	 */
	private void settingsChanged() {
		// check changes and eventually update file tree
		val lastTreeState = CptPluginSettingsForm.getLastTreeState();

		if (lastTreeState != null) {
			for (Map.Entry<CptLang, List<CptVirtualFile>> entry : lastTreeState.entrySet()) {
				val cptLang = entry.getKey();
				val cptVirtualFiles = entry.getValue();
				
				for (CptVirtualFile cptVirtualFile : cptVirtualFiles) {
					try {
						boolean needsUpdate = false;

						if (cptVirtualFile.getFile() != null) {
							createParent(cptVirtualFile.getFile());
						}

						if (cptVirtualFile.isNew()) {
							//noinspection ResultOfMethodCallIgnored
							cptVirtualFile.getFile().createNewFile();

							needsUpdate = cptVirtualFile.getUrl() != null;
						}
						if (cptVirtualFile.fileHasChanged()) {
							cptVirtualFile.getOldFile().renameTo(cptVirtualFile.getFile());
						}
						if (cptVirtualFile.urlHasChanged()) {
							needsUpdate = true;
						}

						if (needsUpdate) {
							val content = new Scanner(cptVirtualFile.getUrl().openStream(), "UTF-8").useDelimiter("\\A").next();
							try (BufferedWriter writer = new BufferedWriter(new FileWriter(cptVirtualFile.getFile()))) {
								writer.write(content);
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				val filesInTree = cptVirtualFiles.stream().map(f -> f.getFile().getAbsolutePath()).collect(Collectors.toSet());

				// delete the files that has been removed from the tree
				Arrays.stream(CptUtil.getTemplateFilesFromLanguageDir(cptLang.getLanguage()))
					.filter(f -> !filesInTree.contains(f.getAbsolutePath()))
					.forEach(f -> f.delete());
			}
		}

		ApplicationManager.getApplication().getMessageBus().syncPublisher(SettingsChangedListener.TOPIC).onSettingsChange(this);
	}

	private void createParent(File file) {
		file.getParentFile().mkdirs();
	}

	@NotNull
	@Override
	public CptPluginSettings getPluginSettings() {
		state.pluginSettings.upgrade();
		return state.pluginSettings;
	}


	public static class State {
		@Property(surroundWithTag = false)
		@NotNull
		private CptPluginSettings pluginSettings = CptPluginSettings.DEFAULT;
	}

	public interface SettingsChangedListener {
		Topic<SettingsChangedListener> TOPIC = Topic.create("CustomPostfixTemplatesApplicationSettingsChanged", SettingsChangedListener.class);

		void reloadTemplates();

		void onSettingsChange(@NotNull CptApplicationSettings settings);
	}
}
