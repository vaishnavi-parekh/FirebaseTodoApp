
package com.example.firebasetodoapp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DATABASE_URL =
    "https://fir-todoapp-52e25-default-rtdb.asia-southeast1.firebasedatabase.app"

data class TaskItem(
    val description: String = "",
    val priority: String = "Medium"
)

data class TaskWithId(
    val id: String,
    val task: TaskItem
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var taskReference by remember {
                    mutableStateOf<DatabaseReference?>(null)
                }
                var setupError by remember {
                    mutableStateOf<String?>(null)
                }

                LaunchedEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()

                    fun connectUser(uid: String) {
                        taskReference = FirebaseDatabase
                            .getInstance(DATABASE_URL)
                            .reference
                            .child("users")
                            .child(uid)
                            .child("tasks")
                    }

                    val currentUser = auth.currentUser

                    if (currentUser != null) {
                        connectUser(currentUser.uid)
                    } else {
                        auth.signInAnonymously()
                            .addOnSuccessListener { result ->
                                val uid = result.user?.uid

                                if (uid != null) {
                                    connectUser(uid)
                                } else {
                                    setupError =
                                        "Could not get the user ID."
                                }
                            }
                            .addOnFailureListener { exception ->
                                setupError =
                                    exception.localizedMessage
                                        ?: "Firebase sign-in failed."
                            }
                    }
                }

                when {
                    taskReference != null -> {
                        TodoScreen(reference = taskReference!!)
                    }

                    setupError != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Unable to connect to Firebase",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(setupError ?: "Unknown error")
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Connecting to Firebase...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoScreen(reference: DatabaseReference) {

    var description by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf("Medium") }
    var tasks by remember { mutableStateOf(listOf<TaskWithId>()) }
    var editingTask by remember { mutableStateOf<TaskWithId?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(reference) {
        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                tasks = snapshot.children.mapNotNull { child ->
                    val task = child.getValue(TaskItem::class.java)
                    val id = child.key

                    if (task != null && id != null) {
                        TaskWithId(id, task)
                    } else {
                        null
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                errorMessage = error.message
            }
        }

        reference.addValueEventListener(listener)

        onDispose {
            reference.removeEventListener(listener)
        }
    }

    val sortedTasks = tasks.sortedWith(
        compareBy<TaskWithId> {
            when (it.task.priority) {
                "High" -> 0
                "Medium" -> 1
                "Low" -> 2
                else -> 3
            }
        }.thenBy {
            it.task.description.lowercase()
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FA))
            .padding(16.dp)
    ) {

        Text(
            text = "My To-Do List",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF243B64)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Organize your tasks by priority",
            color = Color.DarkGray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Task description") },
            placeholder = { Text("Enter your task") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            )
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Select priority",
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(6.dp))

        PrioritySelector(
            selected = selectedPriority,
            onSelect = { selectedPriority = it }
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val cleanDescription = description.trim()

                if (cleanDescription.isEmpty()) {
                    errorMessage = "Please enter a task description."
                } else {
                    val newKey = reference.push().key

                    if (newKey == null) {
                        errorMessage = "Could not create a task ID."
                    } else {
                        reference.child(newKey)
                            .setValue(
                                TaskItem(
                                    description = cleanDescription,
                                    priority = selectedPriority
                                )
                            )
                            .addOnSuccessListener {
                                description = ""
                                selectedPriority = "Medium"
                                errorMessage = null
                            }
                            .addOnFailureListener {
                                errorMessage =
                                    it.localizedMessage
                                        ?: "Could not add the task."
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Task")
        }

        Spacer(Modifier.height(16.dp))

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Your Tasks",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${tasks.size} task(s)",
                color = Color.DarkGray
            )
        }

        Spacer(Modifier.height(8.dp))

        if (sortedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tasks yet. Add your first task!",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = sortedTasks,
                    key = { it.id }
                ) { item ->
                    TaskRow(
                        item = item,
                        onEdit = { editingTask = item },
                        onDelete = {
                            reference.child(item.id)
                                .removeValue()
                                .addOnFailureListener {
                                    errorMessage =
                                        it.localizedMessage
                                            ?: "Could not delete the task."
                                }
                        }
                    )
                }
            }
        }
    }

    editingTask?.let { item ->
        EditTaskDialog(
            item = item,
            onDismiss = { editingTask = null },
            onSave = { newDescription, newPriority ->
                val cleanDescription = newDescription.trim()

                if (cleanDescription.isEmpty()) {
                    errorMessage = "Description cannot be empty."
                } else {
                    reference.child(item.id)
                        .setValue(
                            TaskItem(
                                description = cleanDescription,
                                priority = newPriority
                            )
                        )
                        .addOnSuccessListener {
                            editingTask = null
                            errorMessage = null
                        }
                        .addOnFailureListener {
                            errorMessage =
                                it.localizedMessage
                                    ?: "Could not update the task."
                        }
                }
            }
        )
    }
}

@Composable
fun PrioritySelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("High", "Medium", "Low").forEach { priority ->
            FilterChip(
                selected = selected == priority,
                onClick = { onSelect(priority) },
                label = { Text(priority) }
            )
        }
    }
}

@Composable
fun TaskRow(
    item: TaskWithId,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember(item.id) {
        mutableStateOf(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFE5E5))
    ) {
        Text(
            text = "Swipe to delete",
            color = Color(0xFFB3261E),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(offsetX.roundToInt(), 0)
                }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount)
                                .coerceIn(-500f, 500f)
                        },
                        onDragEnd = {
                            val shouldDelete = abs(offsetX) > 160f
                            offsetX = 0f

                            if (shouldDelete) {
                                onDelete()
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.task.description,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                }

                Spacer(Modifier.height(4.dp))

                val priorityColor = when (item.task.priority) {
                    "High" -> Color(0xFFB3261E)
                    "Medium" -> Color(0xFF9A6700)
                    else -> Color(0xFF237A45)
                }

                Text(
                    text = "Priority: ${item.task.priority}",
                    color = priorityColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun EditTaskDialog(
    item: TaskWithId,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var description by remember(item.id) {
        mutableStateOf(item.task.description)
    }

    var priority by remember(item.id) {
        mutableStateOf(item.task.priority)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = false
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "Priority",
                    fontWeight = FontWeight.SemiBold
                )

                PrioritySelector(
                    selected = priority,
                    onSelect = { priority = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(description, priority) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
