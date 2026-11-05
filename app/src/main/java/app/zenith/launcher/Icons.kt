package app.zenith.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icons as Material Icons paths (Apache-2.0) on a 24-unit grid, settings gear from Material Symbols
 * Rounded (Apache-2.0). Avoids the multi-megabyte material-icons-extended artifact for a few glyphs.
 */
object Icons {
    val Next = filled("M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z")
    val Previous = filled("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Done = filled("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Home = filled("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Wallpaper = filled(
        "M4,4h7V2H4c-1.1,0 -2,0.9 -2,2v7h2V4zM10,13l-4,5h12l-3,-4 -2.03,2.71L10,13zM17,8.5c0,-0.83 -0.67,-1.5 -1.5,-1.5" +
            "S14,7.67 14,8.5s0.67,1.5 1.5,1.5S17,9.33 17,8.5zM20,2h-7v2h7v7h2V4c0,-1.1 -0.9,-2 -2,-2zM20,20h-7v2h7" +
            "c1.1,0 2,-0.9 2,-2v-7h-2v7zM4,13H2v7c0,1.1 0.9,2 2,2h7v-2H4v-7z",
    )
    val Clock = filled(
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 " +
            "-8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
    )
    val SwipeUp = filled("M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z")
    val Label = filled("M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z")
    val Apps = filled(
        "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4zM10,14h4v-4h-4v4zM16,4v4h4V4h-4zM10,8h4V4h-4v4z" +
            "M16,14h4v-4h-4v4zM16,20h4v-4h-4v4z",
    )
    val DarkMode = filled(
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 " +
            "-0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z",
    )
    val Dock = filled(
        "M8,23h8v-2H8V23zM16,1.01L8,1C6.9,1 6,1.9 6,3v14c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V3C18,1.9 17.1,1.01 16,1.01z" +
            "M16,15H8V5h8V15z",
    )
    val Dots = filled("M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    val Add = filled("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val AddPage = filled("M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,16h-3v3h-2v-3H8v-2h3v-3h2v3h3v2zM13,9V3.5L18.5,9H13z")
    val Delete = filled("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val Widgets = filled("M13,13v8h8v-8h-8zM3,21h8v-8H3v8zM3,3v8h8V3H3zM16.66,1.69L11,7.34 16.66,13l5.66,-5.66 -5.66,-5.65z")
    val Lock = filled(
        "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10" +
            "c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
    )
    val LockOpen = filled(
        "M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9" +
            "c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10" +
            "c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10z",
    )
    val Columns = filled("M14.67,5v14H9.33V5H14.67zM15.67,19H21V5h-5.33V19zM8.33,19V5H3v14H8.33z")
    val Rows = filled("M21,8H3V4h18V8zM21,10H3v4h18V10zM21,16H3v4h18V16z")
    val IconSize = filled(
        "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5" +
            "c0,-1.1 -0.9,-2 -2,-2zM21,19.01H3V4.99h18v14.02z",
    )
    val Gesture = filled(
        "M4.59,6.89c0.7,-0.71 1.4,-1.35 1.71,-1.22 0.5,0.2 0,1.03 -0.3,1.52 -0.25,0.42 -2.86,3.89 -2.86,6.31 0,1.28 " +
            "0.48,2.34 1.34,2.98 0.75,0.56 1.74,0.73 2.64,0.46 1.07,-0.31 1.95,-1.4 3.06,-2.77 1.21,-1.49 2.83,-3.44 " +
            "4.08,-3.44 1.63,0 1.65,1.01 1.76,1.79 -3.78,0.64 -5.38,3.67 -5.38,5.37 0,1.7 1.44,3.09 3.21,3.09 1.63,0 " +
            "4.29,-1.33 4.69,-6.1L21,14.88v-2.5h-2.47c-0.15,-1.65 -1.09,-4.2 -4.03,-4.2 -2.25,0 -4.18,1.91 -4.94,2.84 " +
            "-0.58,0.73 -2.06,2.48 -2.29,2.72 -0.25,0.3 -0.68,0.84 -1.11,0.84 -0.45,0 -0.72,-0.83 -0.36,-1.92 0.35,-1.09 " +
            "1.4,-2.86 1.85,-3.52 0.78,-1.14 1.3,-1.92 1.3,-3.28C8.95,3.69 7.31,3 6.44,3 5.12,3 3.97,4 3.72,4.25c-0.36,0.36 " +
            "-0.66,0.66 -0.88,0.93l1.75,1.71zM13.88,18.55c-0.31,0 -0.74,-0.26 -0.74,-0.72 0,-0.6 0.73,-2.2 2.87,-2.76 " +
            "-0.3,2.69 -1.43,3.48 -2.13,3.48z",
    )
    val Info = filled(
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z",
    )
    val Settings = symbol(
        "M433-80q-27 0-46.5-18T363-142l-9-66q-13-5-24.5-12T307-235l-62 26q-25 11-50 2t-39-32l-47-82q-14-23-8-49t27-43l5" +
            "3-40q-1-7-1-13.5v-27q0-6.5 1-13.5l-53-40q-21-17-27-43t8-49l47-82q14-23 39-32t50 2l62 26q11-8 23-15t24-12l9-66q" +
            "4-26 23.5-44t46.5-18h94q27 0 46.5 18t23.5 44l9 66q13 5 24.5 12t22.5 15l62-26q25-11 50-2t39 32l47 82q14 23 8 49" +
            "t-27 43l-53 40q1 7 1 13.5v27q0 6.5-2 13.5l53 40q21 17 27 43t-8 49l-48 82q-14 23-39 32t-50-2l-60-26q-11 8-23 15" +
            "t-24 12l-9 66q-4 26-23.5 44T527-80h-94Zm7-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-" +
            "31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-63" +
            "3l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180" +
            "q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z",
    )

    /** A Material Symbols path: viewBox 0 -960 960 960, so the group shifts it down onto the grid. */
    private fun symbol(path: String) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 960f, viewportHeight = 960f,
    )
        .addGroup(translationY = 960f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()

    private fun filled(path: String) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    )
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()
}
