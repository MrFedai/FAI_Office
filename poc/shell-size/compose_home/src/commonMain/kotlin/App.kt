// The same minimal home screen as ../flutter_home: app bar, button, 100-row list.
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("FAI Office PoC") }) }) { pad ->
            Column(Modifier.padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {}, modifier = Modifier.padding(16.dp)) { Text("Open document") }
                LazyColumn {
                    items(100) { i -> ListItem(headlineContent = { Text("Recent file ${i + 1}.docx") }, supportingContent = { Text("Today") }) }
                }
            }
        }
    }
}
