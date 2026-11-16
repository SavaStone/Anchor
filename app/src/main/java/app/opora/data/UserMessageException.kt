package app.opora.data

import android.content.Context
import androidx.annotation.StringRes

/**
 * An error that has a user-facing message in resources. Data and backup code
 * cannot know the current locale, so they carry the resource id and arguments
 * until the UI resolves it with [userMessage].
 */
class UserMessageException(
    @StringRes val messageRes: Int,
    vararg val args: Any
) : IllegalStateException() {
    constructor(messageRes: Int, cause: Throwable) : this(messageRes) {
        initCause(cause)
    }
}

fun Throwable.userMessage(context: Context): String = when (this) {
    is UserMessageException -> context.getString(messageRes, *args)
    else -> message ?: context.getString(app.opora.R.string.errors_unknown)
}
