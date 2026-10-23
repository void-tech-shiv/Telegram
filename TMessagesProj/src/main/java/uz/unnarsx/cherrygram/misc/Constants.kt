/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Copyright github.com/arsLan4k1390, 2022-2026.
 */

package uz.unnarsx.cherrygram.misc

import uz.unnarsx.cherrygram.core.configs.CherrygramCoreConfig

object Constants {

    @JvmField
    var CG_AUTHOR = "GLACIA"

    /** CG Links start */
    @JvmField
    var CG_CHANNEL_USERNAME = ""
    @JvmField
    var CG_CHANNEL_URL = "https://telegram.org/faq"

    @JvmField
    var CG_APKS_CHANNEL_USERNAME = ""
    @JvmField
    var CG_APKS_CHANNEL_URL = "https://telegram.org/faq"

    @JvmField
    var CG_BETA_APKS_CHANNEL_USERNAME = ""
    @JvmField
    var CG_BETA_APKS_CHANNEL_URL = "https://telegram.org/faq"

    @JvmField
    var CG_CHAT_USERNAME = ""
    @JvmField
    var CG_CHAT_URL = "https://telegram.org/faq"

    @JvmField
    var CG_OFFTOPIC_CHAT_USERNAME = ""

    @JvmField
    var UPDATE_APP_URL = if (CherrygramCoreConfig.isPlayStoreBuild()) "https://telegram.org/faq" else CG_CHANNEL_URL

    @JvmField
    var CG_PRIVACY_URL = "https://telegram.org/faq"
    @JvmField
    var CG_DONATIONS_AND_TERMS_URL = "https://telegram.org/faq"

    @JvmField
    var CG_CROWDIN_URL = "https://telegram.org/faq"
    @JvmField
    var CG_GITHUB_URL = "https://github.com/void-tech-shiv/Telegram"
    /** CG Links finish */

    const val PACKAGE_NAME = "dev.glacia.fullclient"

    /** CG Chats IDs start */
    const val Cherrygram_Owner = 0L // Cherrygram Owner (Arslan)
    const val Cherrygram_Channel = 0L // Cherrygram Channel
    const val Cherrygram_Support = 0L // Cherrygram Support Group
    const val Cherrygram_APKs = 0L // Cherrygram APKs
    const val Cherrygram_Beta = 0L // Cherrygram Beta APKs
    const val Cherrygram_Archive = 0L // Cherrygram Archive
    /** CG Chats IDs finish */

    /** OWNer's friends start */
    const val Yuki = 706402791L
    const val Alina = 553511970L
    const val Samir = 5710829964L
    /** OWNer's friends finish */

    /** Misc start */
    const val CHERRY_EMOJI_ID = 0L // Cherrygram logo
    const val CHERRY_EMOJI_ID_BRA = 0L // Cherrygram logo (bra)
    const val CHERRY_EMOJI_ID_VERIFIED = 0L // Cherrygram Verified adaptive logo
    const val CHERRY_EMOJI_ID_VERIFIED_BRA = 0L // Cherrygram Verified Bra adaptive logo
    const val CHERRY_EMOJI_ID_DONATE = 0L // Cherry emoji with eyeglasses
    const val CHERRY_EMOJI_ID_PREMIUM = 0L // Cherry emoji with stars
    const val CHERRY_EMOJI_ID_PREMIUM_MOON = 0L // Evil moon emoji
    const val PROFILE_BACKGROUND_COLOR_ID_GREEN_BLUE = 12 // Blue-Green gradient
    const val PROFILE_BACKGROUND_COLOR_ID_RED = 14 // Red-Pink gradient
    const val REPLY_BACKGROUND_COLOR_ID = 13 // Red-Pink gradient
    /** Misc finish */

}