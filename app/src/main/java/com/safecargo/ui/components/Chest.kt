package com.safecargo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.safecargo.app.R
import com.safecargo.engine.Grade
import com.safecargo.ui.theme.SC

/**
 * 보물상자 G 그림 (11/10 디자이너 전달 투명 PNG, 3배). 바닥 그림자 색만 다크 / 라이트로 다르다.
 * 닫힌 상자 260×206 · 열린 상자 300×262 기준 (가로세로 비 [aspect]).
 * 뚜껑이 분리되지 않은 통짜 그림이라 열림은 「닫힌 그림 → 열린 그림 교체」로 표현한다.
 * 호위 중 금 간 / 크게 금 간 그림(cracked · broken)은 앱이 주행 중 상자 손상을 보여주지 않아(A1) 넣지 않았다.
 */
enum class ChestArt(@DrawableRes val dark: Int, @DrawableRes val light: Int, val aspect: Float) {
    INTACT(R.drawable.chest_intact_dark, R.drawable.chest_intact_light, CLOSED_AR),
    OPEN_RARE(R.drawable.chest_open_rare_dark, R.drawable.chest_open_rare_light, OPEN_AR),
    OPEN_EPIC(R.drawable.chest_open_epic_dark, R.drawable.chest_open_epic_light, OPEN_AR),
    OPEN_LEGENDARY(R.drawable.chest_open_legendary_dark, R.drawable.chest_open_legendary_light, OPEN_AR),
    /** 아쉬운 상자 — 닫힌 상자와 같은 크기 그림. */
    OPEN_EMPTY(R.drawable.chest_open_empty_dark, R.drawable.chest_open_empty_light, CLOSED_AR),
    BONUS_CLOSED(R.drawable.chest_bonus_closed_dark, R.drawable.chest_bonus_closed_light, CLOSED_AR),
    BONUS_OPEN(R.drawable.chest_bonus_open_dark, R.drawable.chest_bonus_open_light, OPEN_AR),
    ;

    companion object {
        /** 등급별 열린 상자. [empty] 면 아쉬운 상자(빈 상자). */
        fun opened(g: Grade, empty: Boolean = false): ChestArt = when {
            empty -> OPEN_EMPTY
            g == Grade.RARE -> OPEN_RARE
            g == Grade.EPIC -> OPEN_EPIC
            else -> OPEN_LEGENDARY
        }
    }
}

const val CLOSED_AR = 260f / 206f
const val OPEN_AR = 300f / 262f

@Composable
fun ChestImage(art: ChestArt, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painterResource(if (SC.palette.isDark) art.dark else art.light),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
