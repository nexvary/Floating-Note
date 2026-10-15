package com.nexvary.floatingnote

/** Light Markdown-formatting shortcuts that keep note files plain and portable. */
object NoteFormatting {
    fun toggleTask(line: String): String = when {
        line.startsWith("- [ ] ") -> "- [x] " + line.removePrefix("- [ ] ")
        line.startsWith("- [x] ") -> "- [ ] " + line.removePrefix("- [x] ")
        else -> "- [ ] " + line
    }

    fun bold(selection: String): String =
        if (selection.startsWith("**") && selection.endsWith("**") && selection.length >= 4)
            selection.removePrefix("**").removeSuffix("**")
        else "**" + selection + "**"
}
