package com.ntt.ecl.webrtc.sample_p2p_videochat;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;

import java.util.ArrayList;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

/**
 *
 * MainActivity.java
 * ECL WebRTC p2p video-chat sample
 *
 */
public class MainActivity extends Activity {
  private static final String TAG = MainActivity.class.getSimpleName();

  //
  // Set your APIkey and Domain
  //
  private static final String API_KEY = "yourAPIKEY";
  private static final String DOMAIN = "yourDomain";

  private Peer _peer;
  private MediaStream _localStream;
  private MediaStream _remoteStream;
  private MediaConnection _mediaConnection;

  private String _strOwnId;
  private boolean _bConnected;

  private Handler _handler;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    _handler = new Handler(Looper.getMainLooper());
    final Activity activity = this;

    //
    // Initialize Peer
    //
    PeerOption option = new PeerOption();
    option.key = API_KEY;
    option.domain = DOMAIN;
    _peer = new Peer(this, option);

    //
    // Set Peer event callbacks
    //

    // OPEN
    _peer.on(Peer.PeerEventEnum.OPEN, object -> {

      // Show my ID
      _strOwnId = (String) object;
      TextView tvOwnId = findViewById(R.id.tvOwnId);
      tvOwnId.setText(_strOwnId);

      // Request permissions
      if (ContextCompat.checkSelfPermission(activity,
        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},0);
      }
      else {

        // Get a local MediaStream & show it
        startLocalStream();
      }

    });

    // CALL (Incoming call)
    _peer.on(Peer.PeerEventEnum.CALL, object -> {
      if (!(object instanceof MediaConnection)) {
        return;
      }

      _mediaConnection = (MediaConnection) object;
      setMediaCallbacks();
      _mediaConnection.answer(_localStream);

      _bConnected = true;
      updateActionButtonTitle();
    });

    _peer.on(Peer.PeerEventEnum.CLOSE, object -> Log.d(TAG, "[On/Close]"));
    _peer.on(Peer.PeerEventEnum.ERROR, object -> {
      PeerError error = (PeerError) object;
      Log.d(TAG, "[On/Error]" + error.getMessage());
    });


    //
    // Set GUI event listeners
    //

    Button btnAction = findViewById(R.id.btnAction);
    btnAction.setEnabled(true);
    btnAction.setOnClickListener(v -> {
      v.setEnabled(false);

      if (!_bConnected) {

        // Select remote peer & make a call
        showPeerIDs();
      }
      else {

        // Hang up a call
        closeRemoteStream();
        _mediaConnection.close(true);

      }

      v.setEnabled(true);
    });
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == 0) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startLocalStream();
      } else {
        Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  protected void onStart() {
    super.onStart();

    // Hide the status bar.
    View decorView = getWindow().getDecorView();
    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

    // Disable Sleep and Screen Lock
    Window wnd = getWindow();
    wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Set volume control stream type to WebRTC audio.
    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
  }

  @Override
  protected void onPause() {
    // Set default volume control stream type.
    setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
    super.onPause();
  }

  @Override
  protected void onStop() {
    // Enable Sleep and Screen Lock
    Window wnd = getWindow();
    wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    destroyPeer();
    super.onDestroy();
  }

  //
  // Get a local MediaStream & show it
  //
  void startLocalStream() {
    Navigator.initialize(_peer);
    MediaConstraints constraints = new MediaConstraints();
    _localStream = Navigator.getUserMedia(constraints);

    Canvas canvas = findViewById(R.id.svLocalView);
    _localStream.addVideoRenderer(canvas,0);
  }

  //
  // Set callbacks for MediaConnection.MediaEvents
  //
  void setMediaCallbacks() {

    _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, object -> {
      _remoteStream = (MediaStream) object;
      Canvas canvas = findViewById(R.id.svRemoteView);
      _remoteStream.addVideoRenderer(canvas,0);
    });

    _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, object -> {
      closeRemoteStream();
      _bConnected = false;
      updateActionButtonTitle();
    });

    _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, object -> {
      PeerError error = (PeerError) object;
      Log.d(TAG, "[On/MediaError]" + error);
    });

  }

  //
  // Clean up objects
  //
  private void destroyPeer() {
    closeRemoteStream();

    if (null != _localStream) {
      Canvas canvas = findViewById(R.id.svLocalView);
      _localStream.removeVideoRenderer(canvas,0);
      _localStream.close();
    }

    if (null != _mediaConnection) {
      if (_mediaConnection.isOpen()) {
        _mediaConnection.close(true);
      }
      unsetMediaCallbacks();
    }

    Navigator.terminate();

    if (null != _peer) {
      unsetPeerCallback(_peer);

      if (!_peer.isDestroyed()) {
        _peer.destroy();
      }

      _peer = null;
    }
  }

  //
  // Unset callbacks for PeerEvents
  //
  void unsetPeerCallback(Peer peer) {
    if(null == _peer){
      return;
    }

    peer.on(Peer.PeerEventEnum.OPEN, null);
    peer.on(Peer.PeerEventEnum.CONNECTION, null);
    peer.on(Peer.PeerEventEnum.CALL, null);
    peer.on(Peer.PeerEventEnum.CLOSE, null);
    peer.on(Peer.PeerEventEnum.ERROR, null);
  }

  //
  // Unset callbacks for MediaConnection.MediaEvents
  //
  void unsetMediaCallbacks() {
    if(null == _mediaConnection){
      return;
    }

    _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
    _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
    _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
  }

  //
  // Close a remote MediaStream
  //
  void closeRemoteStream(){
    if (null == _remoteStream) {
      return;
    }

    Canvas canvas = findViewById(R.id.svRemoteView);
    _remoteStream.removeVideoRenderer(canvas,0);
    _remoteStream.close();
  }

  //
  // Create a MediaConnection
  //
  void onPeerSelected(String strPeerId) {
    if (null == _peer) {
      return;
    }

    if (null != _mediaConnection) {
      _mediaConnection.close(true);
    }

    CallOption option = new CallOption();
    _mediaConnection = _peer.call(strPeerId, _localStream, option);

    if (null != _mediaConnection) {
      setMediaCallbacks();
      _bConnected = true;
    }

    updateActionButtonTitle();
  }

  //
  // Listing all peers
  //
  void showPeerIDs() {
    if ((null == _peer) || (null == _strOwnId) || (0 == _strOwnId.length())) {
      Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show();
      return;
    }

    // Get all IDs connected to the server
    final Context fContext = this;
    _peer.listAllPeers(object -> {
      if (!(object instanceof JSONArray)) {
        return;
      }

      JSONArray peers = (JSONArray) object;
      ArrayList<String> _listPeerIds = new ArrayList<>();
      String peerId;

      // Exclude my own ID
      for (int i = 0; peers.length() > i; i++) {
        try {
          peerId = peers.getString(i);
          if (!_strOwnId.equals(peerId)) {
            _listPeerIds.add(peerId);
          }
        } catch(Exception e){
          e.printStackTrace();
        }
      }

      // Show IDs using DialogFragment
      if (0 < _listPeerIds.size()) {
        FragmentManager mgr = getFragmentManager();
        PeerListDialogFragment dialog = new PeerListDialogFragment();
        dialog.setListener(
          item -> _handler.post(() -> onPeerSelected(item)));
        dialog.setItems(_listPeerIds);
        dialog.show(mgr, "peerlist");
      }
      else{
        Toast.makeText(fContext, "PeerID list (other than your ID) is empty.", Toast.LENGTH_SHORT).show();
      }
    });

  }

  //
  // Update actionButton title
  //
  void updateActionButtonTitle() {
    _handler.post(() -> {
      Button btnAction = findViewById(R.id.btnAction);
      if (null != btnAction) {
        if (!_bConnected) {
          btnAction.setText("Make Call");
        } else {
          btnAction.setText("Hang up");
        }
      }
    });
  }

}
