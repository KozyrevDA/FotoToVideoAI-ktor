package extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

suspend fun watchFile(path: String, onChange: suspend () -> Unit) = withContext(Dispatchers.IO) {
    val file = File(path).absoluteFile.toPath()
    val watchService = FileSystems.getDefault().newWatchService()
    file.parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

    watchService.use { watch ->
        while (isActive) {
            val key = watch.take()
            for (event in key.pollEvents()) {
                if ((event.context() as? Path)?.fileName == file.fileName) {
                    onChange()
                }
            }
            key.reset()
        }
    }
}