package fr.bonamy.sports.mobile;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        registerPlugin(SportsHttpPlugin.class);
        super.onCreate(state);
    }
}
