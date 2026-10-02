package net.puffish.skillsmod.config.reader;

import net.puffish.skillsmod.api.json.JsonElement;
import net.puffish.skillsmod.api.json.JsonPath;
import net.puffish.skillsmod.api.util.Problem;
import net.puffish.skillsmod.util.PathUtils;
import net.puffish.skillsmod.api.util.Result;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileConfigReader extends ConfigReader {
	private final Path modConfigDir;

	public FileConfigReader(Path modConfigDir) {
		this.modConfigDir = modConfigDir;
	}

	public Result<JsonElement, Problem> readFile(Path file) {
		return JsonElement.parseFile(
				file,
				JsonPath.create(modConfigDir.relativize(file).toString())
		);
	}

	@Override
	public Result<JsonElement, Problem> read(Path path) {
		return readFile(modConfigDir.resolve(path));
	}

	@Override
	public boolean exists(Path path) {
		return Files.exists(modConfigDir.resolve(path));
	}
}
