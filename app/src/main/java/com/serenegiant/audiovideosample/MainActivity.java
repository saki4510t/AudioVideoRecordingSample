package com.serenegiant.audiovideosample;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MainActivity.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.serenegiant.dialog.MessageDialogFragmentV4;
import com.serenegiant.system.BuildCheck;
import com.serenegiant.system.PermissionCheck;

public class MainActivity extends AppCompatActivity
	implements MessageDialogFragmentV4.MessageDialogListener {

	private static final boolean DEBUG = false;	// XXX 実働時はfalseにすること
	private static final String TAG = MainActivity.class.getSimpleName();

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
				.add(R.id.container, new CameraFragment()).commit();
		}
	}

	@Override
	protected final void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		if (BuildCheck.isAndroid7()) {
			internalOnResume();
		}
	}

	@Override
	protected final void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		if (!BuildCheck.isAndroid7()) {
			internalOnResume();
		}
	}

	@Override
	protected final void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		if (!BuildCheck.isAndroid7()) {
			internalOnPause();
		}
		super.onPause();
	}

	@Override
	protected final void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		if (BuildCheck.isAndroid7()) {
			internalOnPause();
		}
		super.onStop();
	}

	protected void internalOnResume() {
		if (DEBUG) Log.v(TAG, "internalOnResume:");
		checkPermission();
	}

	protected void internalOnPause() {
		if (DEBUG) Log.v(TAG, "internalOnPause:");
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		final int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode,
		@NonNull final String[] permissions, @NonNull final int[] grantResults) {

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);    // 何もしてないけど一応呼んどく
		final int n = Math.min(permissions.length, grantResults.length);
		for (int i = 0; i < n; i++) {
			checkPermissionResult(requestCode, permissions[i],
				grantResults[i] == PackageManager.PERMISSION_GRANTED);
		}
		checkPermission();
	}

	/**
	 * callback listener from MessageDialogFragmentV4
	 *
	 * @param dialog
	 * @param requestCode
	 * @param permissions
	 * @param result
	 */
	@SuppressLint("NewApi")
	@Override
	public void onMessageDialogResult(
		@NonNull final MessageDialogFragmentV4 dialog, final int requestCode,
		@NonNull final String[] permissions, final boolean result) {

		switch (requestCode) {
		case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE:
		case REQUEST_PERMISSION_AUDIO_RECORDING:
		case REQUEST_PERMISSION_CAMERA:
		case REQUEST_PERMISSION_NETWORK:
		case REQUEST_PERMISSION_HARDWARE_ID:
		case REQUEST_PERMISSION_LOCATION:
			if (result) {
				// メッセージダイアログでOKを押された時はパーミッション要求する
				if (BuildCheck.isMarshmallow()) {
					requestPermissions(permissions, requestCode);
					return;
				}
			}
			// メッセージダイアログでキャンセルされた時とAndroid6でない時は自前でチェックして#checkPermissionResultを呼び出す
			for (final String permission : permissions) {
				checkPermissionResult(requestCode, permission,
					PermissionCheck.hasPermission(this, permission));
			}
			break;
		}
	}

//--------------------------------------------------------------------------------
	private boolean checkPermission() {
		return checkPermissionCamera()
			&& checkPermissionAudio()
			&& checkPermissionWriteExternalStorage();
	}

	private static final int ID_PERMISSION_REASON_AUDIO = R.string.permission_audio_recording_reason;
	private static final int ID_PERMISSION_REQUEST_AUDIO = R.string.permission_audio_recording_request;
	private static final int ID_PERMISSION_REASON_NETWORK = R.string.permission_network_reason;
	private static final int ID_PERMISSION_REQUEST_NETWORK = R.string.permission_network_request;
	private static final int ID_PERMISSION_REASON_EXT_STORAGE = R.string.permission_ext_storage_reason;
	private static final int ID_PERMISSION_REQUEST_EXT_STORAGE = R.string.permission_ext_storage_request;
	private static final int ID_PERMISSION_REASON_CAMERA = R.string.permission_camera_reason;
	private static final int ID_PERMISSION_REQUEST_CAMERA = R.string.permission_camera_request;
	private static final int ID_PERMISSION_REQUEST_HARDWARE_ID = R.string.permission_hardware_id_request;
	private static final int ID_PERMISSION_REASON_LOCATION = R.string.permission_location_reason;
	private static final int ID_PERMISSION_REQUEST_LOCATION = R.string.permission_location_request;

	/** request code for WRITE_EXTERNAL_STORAGE permission */
	private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 0x1234;
	/** request code for RECORD_AUDIO permission */
	private static final int REQUEST_PERMISSION_AUDIO_RECORDING = 0x2345;
	/** request code for CAMERA permission */
	private static final int REQUEST_PERMISSION_CAMERA = 0x3456;
	/** request code for INTERNET permission */
	private static final int REQUEST_PERMISSION_NETWORK = 0x4567;
	/** request code for READ_PHONE_STATE permission */
	private static final int REQUEST_PERMISSION_HARDWARE_ID = 0x5678;
    /** request code for ACCESS_FINE_LOCATION permission */
	private static final int REQUEST_PERMISSION_LOCATION = 0x6789;

	/**
	 * パーミッション処理の実態
	 * パーミッションが無いときにメッセージを表示するだけ
	 * @param requestCode
	 * @param permission
	 * @param result
	 */
	protected void checkPermissionResult(final int requestCode,
		final String permission, final boolean result) {

		// パーミッションがないときにはメッセージを表示する
		if (!result && (permission != null)) {
			final StringBuilder sb = new StringBuilder();
			if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
				sb.append(getString(R.string.permission_audio));
			}
			if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
				if (sb.length() != 0) {
					sb.append("\n");
				}
				sb.append(getString(R.string.permission_ext_storage));
			}
			if (Manifest.permission.CAMERA.equals(permission)) {
				if (sb.length() != 0) {
					sb.append("\n");
				}
				sb.append(getString(R.string.permission_camera));
			}
			if (Manifest.permission.INTERNET.equals(permission)) {
				if (sb.length() != 0) {
					sb.append("\n");
				}
				sb.append(getString(R.string.permission_network));
			}
			if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) {
				if (sb.length() != 0) {
					sb.append("\n");
				}
				sb.append(getString(R.string.permission_location));
			}
			Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * check permission to access external storage
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access external storage
	 */
	protected boolean checkPermissionWriteExternalStorage() {
		if (!PermissionCheck.hasWriteExternalStorage(this)) {
			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE,
				R.string.permission_title, ID_PERMISSION_REQUEST_EXT_STORAGE,
				new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
			return false;
		}
		return true;
	}

	/**
	 * check permission to record audio
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to record audio
	 */
	protected boolean checkPermissionAudio() {
		if (!PermissionCheck.hasAudio(this)) {
			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_AUDIO_RECORDING,
				R.string.permission_title, ID_PERMISSION_REQUEST_AUDIO,
				new String[]{Manifest.permission.RECORD_AUDIO});
			return false;
		}
		return true;
	}

	/**
	 * check permission to access internal camera
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access internal camera
	 */
	protected boolean checkPermissionCamera() {
		if (!PermissionCheck.hasCamera(this)) {
			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_CAMERA,
				R.string.permission_title, ID_PERMISSION_REQUEST_CAMERA,
				new String[]{Manifest.permission.CAMERA});
			return false;
		}
		return true;
	}

	/**
	 * check permission to access network
	 * and request to show detail dialog to request permission
	 *
	 * @return true already have permission to access network
	 */
	protected boolean checkPermissionNetwork() {
		if (!PermissionCheck.hasNetwork(this)) {
			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_NETWORK,
				R.string.permission_title, ID_PERMISSION_REQUEST_NETWORK,
				new String[]{Manifest.permission.INTERNET});
			return false;
		}
		return true;
	}

	/**
	 * check permission to access gps
	 * and request to show detail dialog to request permission
	 * @return true already have permission to access gps
	 */
	protected boolean checkPermissionLocation(){
		if (!PermissionCheck.hasAccessLocation(this)) {
			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_LOCATION,
					R.string.permission_title, ID_PERMISSION_REQUEST_LOCATION,
					new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});
			return false;
		}
		return true;
	}
	/**
	 * check permission to of READ_PHONE_STATE
	 * and request to show detail dialog to request permission
	 * This permission is necessarily to get hardware ID on device like IMEI.
	 *
	 * @return true already have permission of READ_PHONE_STATE
	 */
	protected boolean checkPermissionHardwareId() {
		if (!PermissionCheck.hasPermission(this,
			Manifest.permission.READ_PHONE_STATE)) {

			MessageDialogFragmentV4.showDialog(this, REQUEST_PERMISSION_HARDWARE_ID,
				R.string.permission_title, ID_PERMISSION_REQUEST_HARDWARE_ID,
				new String[]{Manifest.permission.READ_PHONE_STATE});
			return false;
		}
		return true;
	}

}
