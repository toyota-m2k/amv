package com.michael.video.v2

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.michael.video.*
import com.michael.video.v2.elements.AmvExoVideoPlayer
import com.michael.video.v2.elements.AmvVideoController
import com.michael.video.v2.viewmodel.FullControllerViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 普通の動画プレーヤー
 */
class AmvPlayerUnitView@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger = AmvSettings.logger
    }

    private val player:AmvExoVideoPlayer by lazy { findViewById(R.id.evp_videoPlayer) }
    private val controller:AmvVideoController by lazy { findViewById(R.id.evp_videoController) }
    private val fitter = AmvFitterEx(FitMode.Width)
    private lateinit var viewModel:FullControllerViewModel

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_player_unit_view, this)
    }

    fun bindViewModel(viewModel:FullControllerViewModel, binder:Binder) {
        this.viewModel = viewModel
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope
        player.bindViewModel(viewModel.playerViewModel, binder)
        controller.bindViewModel(viewModel, binder)

        viewModel.controllerMinWidth.filter { it>0 }.onEach {
            findViewById<View>(R.id.min_width_keeper).setLayoutWidth(it)
        }.launchIn(scope)
//        binder.register()
    }


    // AmvPlayerUnitViewは、動画プレーヤー(player:AmvExoVideoPlayer)を基準に、全体のサイズや各パーツの配置、サイズが決定される。
    // Aspectを維持してリサイズするには、playerWidthプロパティでプレーヤーの幅を指定する。
    // Aspectを呼び出し元で管理するなら、setPlayerSize()で直接プレーヤーのサイズを指定することも可。

    /**
     * 動画プレーヤーの幅を設定（＆取得）する。
     * 高さは、動画のアスペクトに従って自動調整される。
     */
    var playerWidth:Int
        get() = player.width
        set(v) {    // widthを指定して、ソース動画のaspectを維持するようにheightを決定する
            if(v<=0) return
            val vs = viewModel.playerViewModel.videoSize.value ?: return
            val fit = fitter.setLayoutWidth(v).fit(vs.width, vs.height).result
            player.setLayoutSize(fit.width.toInt(), fit.height.toInt())
//            logger.debug("AmvPlayerUnitView:size = ${fitter.resultSize}")
        }

    /**
     * 動画プレーヤーのサイズを指定する。
     * 他のパーツは、動画プレーヤーのサイズに合わせて適当にリサイズ＆移動される。
     */
    fun setPlayerSize(width:Int, height:Int) {
        player.setLayoutSize(width, height)
    }
}