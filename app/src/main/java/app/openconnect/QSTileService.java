/*
 * Copyright (c) 2019.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package app.openconnect;

import android.annotation.TargetApi;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;

/**
 * @author Terry E-mail: yaoxinghuo at qq dot com
 * @date 2019-6-2 18:33
 * @description
 */
@TargetApi(24)
public class QSTileService extends TileService {

    private static final String TAG = QSTileService.class.getName();

    private int mConnectionState;
    private VPNConnector mConn;

    @Override
    public void onStartListening() {
        super.onStartListening();
        mConn = new VPNConnector(this, true) {
            @Override
            public void onUpdate(OpenVpnService service) {
                updateState(service);
            }
        };
    }

    @Override
    public void onStopListening() {
        super.onStopListening();

        mConn.stopActiveDialog();
        mConn.unbind();
    }

    @Override
    public void onClick() {
        super.onClick();

        toggle();
    }

    private void updateState(OpenVpnService service) {
        int newState = service.getConnectionState();
//        Log.d(TAG, "tile status update, state: " + newState + ", last state: " + mConnectionState);

//        service.startActiveDialog(this);

        if (mConnectionState != newState) {
            String tileLabel = null;
            int tileState;
            String profileName;
            switch (newState) {
                case OpenConnectManagementThread.STATE_CONNECTED:
                    tileState = Tile.STATE_ACTIVE;
                    tileLabel = getString(R.string.disconnect);
                    profileName = service.getReconnectName();
                    if (profileName != null) {
                        tileLabel = tileLabel + " " + profileName;
                    }
                    Toast.makeText(this, getString(R.string.state_connected_to, service.profile.getName()), Toast.LENGTH_SHORT).show();
                    break;
                case OpenConnectManagementThread.STATE_DISCONNECTED:
                    tileState = Tile.STATE_INACTIVE;
                    profileName = service.getReconnectName();
                    if (profileName != null) {
                        tileLabel = getString(R.string.reconnect_to, profileName);
                    }
                    break;
                default:
                    tileLabel = service.getConnectionStateName();
                    tileState = Tile.STATE_UNAVAILABLE;
                    break;
            }
            mConnectionState = newState;

            if (tileLabel == null) {
                tileLabel = getString(R.string.app);
            }

            Tile tile = getQsTile();
            tile.setState(tileState);
            tile.setLabel(tileLabel);
            Log.d(TAG, "set tile state: " + tileState + ", label: " + tileLabel);
            tile.updateTile();
        }


    }

    private void toggle() {
        Log.d(TAG, "tile toggle, current state: " + mConnectionState);
        if (mConnectionState == OpenConnectManagementThread.STATE_CONNECTED) {
            mConn.service.stopVPN();
        } else if (mConnectionState == OpenConnectManagementThread.STATE_DISCONNECTED) {
            mConn.service.startReconnectActivity(this);
        }
    }
}
