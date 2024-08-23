# matrix-filter-game-url

This will handle the redirects when the user close the game

## Install

```bash
npm install matrix-filter-game-url
npx cap sync
```

## API

<docgen-index>



</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

</docgen-api>

# Use the plugin
## Add this code in MainActivity in onCreate function

```java

import com.matrix.filtergameurl.NAIFilterGameUrlPlugin;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.bridge.setWebViewClient(new NAIFilterGameUrlPlugin(this.bridge));
  }
}
```