package app.marlboroadvance.mpvex.ui.browser.shorts

import android.util.Xml
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.MPVView
import org.xmlpull.v1.XmlPullParser

@Composable
fun ShortsPlayerHost(
    modifier: Modifier = Modifier,
    onReady: (MPVView) -> Unit
) {
    val context = LocalContext.current
    
    val mpvView = remember {
        // Use a real AttributeSet from a dummy XML layout to avoid ClassCastException
        // and satisfy the non-null requirement of BaseMPVView.
        val parser = context.resources.getLayout(R.layout.shorts_dummy_layout)
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            // Seek to the start tag
        }
        val attrs = Xml.asAttributeSet(parser)
        
        MPVView(context, attrs).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(Unit) {
        mpvView.initialize(context.filesDir.path, context.cacheDir.path)
        onReady(mpvView)
        onDispose {
            mpvView.destroy()
        }
    }

    AndroidView(
        factory = {
            FrameLayout(context).apply {
                addView(mpvView)
            }
        },
        modifier = modifier
    )
}
