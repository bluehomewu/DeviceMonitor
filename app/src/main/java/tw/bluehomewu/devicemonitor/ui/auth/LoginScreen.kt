package tw.bluehomewu.devicemonitor.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.bluehomewu.devicemonitor.R
import tw.bluehomewu.devicemonitor.ui.pairing.JoinWithCodeDialog

@Composable
fun LoginScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    var showJoinDialog by remember { mutableStateOf(false) }

    if (showJoinDialog) {
        JoinWithCodeDialog(
            isLoading = state is AuthState.Loading,
            error = (state as? AuthState.Error)?.message,
            onConfirm = { code -> vm.joinWithCode(code) },
            onDismiss = { showJoinDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))

        when (state) {
            AuthState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            else -> {
                Button(
                    onClick = { vm.signIn(activity) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sign_in_google))
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("以配對碼加入（無 Google 服務）")
                }

                if (state is AuthState.Error && !showJoinDialog) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (state as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
