package com.dtest.testdrive

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.dtest.testdrive.ui.theme.TestDriveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A Google Drive demo app to navigate folders ! it doesn't show files
 * We just show the first page of results. A real app should load more pages when user scrolls down the list
 */
class MainActivity : ComponentActivity() {
    val TAG="por/testDrive"

    val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"

    lateinit var credential: GoogleAccountCredential
    lateinit var drive:Drive

    // will be set to true when permissions are granted and drive object created
    var initialized = mutableStateOf(false)

    var accountName = mutableStateOf<String?>(null)

    // the first element will be the top folder, the last will be the current folder
    // I keep this info here because in Drive it seems that an file can have multiple parents SO I need to go where to go when user clicks UP
    var folderNavigationStack = mutableStateListOf<File>()

    val currentFolder:File?
        get() = folderNavigationStack.lastOrNull()

    // will be null while we are loading the content
    var currentFolderContent = mutableStateOf<List<File>?>(null)

    private val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
    ) { resultOk ->
        Log.v(TAG, "permissionLauncher result=$resultOk")
        if (resultOk.values.all { it }){
            initDrive()
        }
    }

    val chooseAccountLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.extras != null){
            folderNavigationStack.clear()

            accountName.value = result.data!!.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            credential.selectedAccountName = accountName.value

            Log.v(TAG, "accountName changed to ${accountName.value}")

            initFolders()
        }
    }

    val userRecoverableAuthLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK){
            Log.v(TAG, "userRecoverableAuthLauncher result ok")
            initFolders()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, ex: Throwable ->
            if (ex is UserRecoverableAuthIOException) {
                Log.v(TAG, "error auth", ex)

                userRecoverableAuthLauncher.launch(ex.intent)

            }else{
                Log.e(TAG, "UncaughtException", ex)
            }
        }

        permissionLauncher.launch(arrayOf(Manifest.permission.GET_ACCOUNTS))

        setContent {
            TestDriveTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (! initialized.value) {
                        Text("Initializing...")

                    }else{
                        Column(modifier=Modifier.padding(10.dp)) {
                            TopBox(modifier = Modifier.clickable {
                                chooseAccountLauncher.launch(credential.newChooseAccountIntent())
                            }) {
                                Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("User:")
                                    Text(accountName.value ?: "<None>", fontSize = 24.sp)
                                }
                            }

                            if (accountName.value != null){
                                Spacer(Modifier.height(20.dp))

                                TopBox{
                                    Column{
                                        if (folderNavigationStack.isEmpty() || currentFolderContent.value == null) {
                                            Text("Searching...")

                                        }else {
                                            Row{
                                                Text(currentFolder?.name + ":", fontSize = 24.sp)

                                                if (folderNavigationStack.size > 1){

                                                    // user can also press BACK key to go to parent folder
                                                    BackHandler(true) {
                                                        Log.d(TAG, "OnBackPressed")
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            folderNavigationStack.removeLast()
                                                            updateFolderContent()
                                                        }
                                                    }

                                                    Spacer(Modifier.weight(1f).fillMaxWidth())

                                                    Button(onClick = {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            folderNavigationStack.removeLast()
                                                            updateFolderContent()
                                                        }
                                                    }){
                                                        Text("Up")
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(20.dp))

                                            LazyColumn(state = rememberLazyListState()) {
                                                items(currentFolderContent.value ?: listOf()) { f->
                                                    Divider(color = Color.Blue, thickness = 1.dp)

                                                    Text(f.name, Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 5.dp).clickable {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            folderNavigationStack.add(f)
                                                            updateFolderContent()
                                                        }
                                                    })
                                                }
                                            }

                                            Button(onClick={
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    test()
                                                }
                                            }){
                                                Text("Test")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initDrive() {
        Log.v(TAG, "initDrive")

        // scopes:
        // DriveScopes.DRIVE - View and manage the files in your Google Drive.
        // vs DRIVE_FILE - View and manage Google Drive files and folders that you have opened or created with this app.
        credential = GoogleAccountCredential.usingOAuth2(this, arrayListOf(DriveScopes.DRIVE)).setBackOff(ExponentialBackOff())
        Log.v(TAG, "cred=$credential")

        // semelhante a api Calendar!!!
        drive = Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        JacksonFactory.getDefaultInstance(),
                        credential
                )
                .setApplicationName(getString(R.string.app_name))
                .build()

        // isGooglePlayServicesAvailable
        // Verifies that Google Play services is installed and enabled on this device, and that the version installed on this device is no older than the one required by this client.
        // https://developers.google.com/android/reference/com/google/android/gms/common/GoogleApiAvailability#isGooglePlayServicesAvailable(android.content.Context)
        val apiAvailability = GoogleApiAvailability.getInstance()
        val apiStatus =apiAvailability.isGooglePlayServicesAvailable(this)

        if (apiStatus  != ConnectionResult.SUCCESS){
            // api nao disponivel!
            // a app iria mostrar um ecran com msg de erro, assim
            // GoogleApiAvailability.getInstance().getErrorDialog(...).show()
            // Returns a dialog to address the provided errorCode. The returned dialog displays a localized message about the
            // error and upon user confirmation (by tapping on dialog) will direct them to the Play Store if Google Play services is out
            // of date or missing, or to system settings if Google Play services is disabled on the device.
            throw Exception(apiAvailability.getErrorString(apiStatus))
        }

        val connMgr =getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connOk = connMgr.activeNetworkInfo?.isConnected == true
        Log.v(TAG, "connOk=$connOk")

        initialized.value = true
    }

    fun initFolders(){
        Log.v(TAG, "initFolders")

        CoroutineScope(Dispatchers.IO).launch {
            folderNavigationStack.clear()

            // get a random file, don't follow it's parents until there is none. That should be the top folder "My Drive"
            val filesList = drive.Files().list().apply{
                spaces = "drive"
                fields = "files(id, name, parents)"
            }.execute()

            // a random start folder
            if (filesList.isEmpty()) throw Exception("No files found")
            var f = filesList.files[0]

            // no go up until find the top folder
            while (f.parents != null){
                val parentId = f.parents[0] // e se tiver varios? escolho apenas 1
                f =drive.Files().get(parentId).apply{ fields = "id, name, parents"}.execute()
            }

            // f is the top folder from this gdrive
            // note: f.name will by "My Drive" if account language is English, "O meu disco" if portuguese, etc
            folderNavigationStack.add(f)

            updateFolderContent()
        }
    }

    fun updateFolderContent(){
        currentFolderContent.value = null

        val filesList = drive.Files().list().apply {
            // The space in which the IDs can be used to create new files. Supported values are drive and appDataFolder. (Default: drive)
            spaces = "drive"

            // only sub folders of current folder, and ignore trashed files
            q = "'${currentFolder!!.id}' in parents and mimeType='$MIME_TYPE_FOLDER' and trashed=false"

            // to list all files...
            //     q = "'${currentFolder!!.id}' in parents and trashed=false"

            fields = "nextPageToken, files(id, name, parents, mimeType)"

            orderBy = "name"
        }.execute()

        currentFolderContent.value = filesList.files.filter{it.mimeType == MIME_TYPE_FOLDER}
    }


    fun test(){
        Log.v(TAG, "test")

        val user=drive.About().get().apply{fields="*"}.execute().user
        Log.v(TAG, "EMAIL:" + user.emailAddress)

        // null
        val teamDriveId =drive.Teamdrives().list().execute().teamDrives.firstOrNull()?.id
        Log.v(TAG, "teamDrive=$teamDriveId")

        val filesList = drive.Files().list().apply {
            // The space in which the IDs can be used to create new files. Supported values are drive and appDataFolder. (Default: drive)
            spaces = "drive"

            // only sub folders of current folder, and ignore trashed files
            q = "'root' in parents"
            fields = "nextPageToken, files(id, name, parents, mimeType)"
        }.execute()

        val f = filesList.files.firstOrNull()
        Log.v(TAG, "test result: ${filesList.files.size}, id=${f?.id}, name=${f?.name}")

    }
}

// a round rectangle box
@Composable
fun TopBox(modifier: Modifier=Modifier, content: @Composable ()->Unit){
    Box(modifier = modifier //.height(60.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Gray)){

        Box(modifier=Modifier.padding(horizontal = 10.dp, vertical = 10.dp)){
            content()
        }
    }
}