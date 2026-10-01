package com.androiddrop.host;

import static com.androiddrop.security.DeviceAuthFilter.DEVICE;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.androiddrop.device.Device;
import com.androiddrop.host.SaveFolders.Folder;
import com.androiddrop.security.Access;
import com.androiddrop.storage.StorageLayout;

@RestController
@RequestMapping("/api/host")
public class HostController {

	/** Where accepted files can go, and whether "Choose folder…" can open this computer's folder picker. */
	public record FoldersView(List<Folder> folders, boolean canChoose) {
	}

	public record DefaultFolderRequest(String path) {
	}

	private final HostInfo hostInfo;
	private final StorageLayout storage;
	private final SaveFolders saveFolders;

	public HostController(HostInfo hostInfo, StorageLayout storage, SaveFolders saveFolders) {
		this.hostInfo = hostInfo;
		this.storage = storage;
		this.saveFolders = saveFolders;
	}

	@PostMapping("/open-folder")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void openFolder(@RequestAttribute(DEVICE) Device me) throws IOException {
		Access.requireHost(me);
		hostInfo.open(storage.root());
	}

	@GetMapping("/folders")
	public FoldersView folders(@RequestAttribute(DEVICE) Device me) {
		Access.requireHost(me);
		return new FoldersView(saveFolders.choices(), hostInfo.canChooseFolder());
	}

	/** Where accepted files go unless another folder is picked when accepting. */
	@PutMapping("/folders/default")
	public FoldersView setDefaultFolder(@RequestAttribute(DEVICE) Device me, @RequestBody DefaultFolderRequest request) {
		Access.requireHost(me);
		saveFolders.setDefault(saveFolders.validate(request.path()));
		return folders(me);
	}

	/** Opens the folder picker on this computer's screen. No content if it was cancelled. */
	@PostMapping("/folders/choose")
	public ResponseEntity<Folder> chooseFolder(@RequestAttribute(DEVICE) Device me) {
		Access.requireHost(me);
		return hostInfo.chooseFolder("Choose where AndroidDrop saves these files", storage.root())
			.map(path -> ResponseEntity.ok(saveFolders.describe(saveFolders.validate(path.toString()))))
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

}
