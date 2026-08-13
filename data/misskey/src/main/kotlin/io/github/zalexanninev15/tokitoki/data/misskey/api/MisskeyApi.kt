package io.github.zalexanninev15.tokitoki.data.misskey.api

import io.github.zalexanninev15.tokitoki.data.misskey.dto.AntennaDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.FollowingDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.FollowingRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.AntennaNotesRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.CredentialRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.MiAuthCheckDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.MisskeyUserDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.NodeInfoDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.NoteDto
import io.github.zalexanninev15.tokitoki.data.misskey.dto.NotificationsReadRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.UserNotesRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.UserShowRequest
import io.github.zalexanninev15.tokitoki.data.misskey.dto.TimelineRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Misskey's API is POST-with-a-JSON-body for essentially everything, including reads, and
 * the token travels in the body as `i` rather than in an Authorization header. That is why
 * this interface looks nothing like [MastodonApi] and why the two are not unified.
 */
interface MisskeyApi {

    /** Used to detect the server flavour and pick an auth mechanism. */
    @GET("nodeinfo/2.0")
    suspend fun nodeInfo(): NodeInfoDto

    /** Second half of the MiAuth flow, after the user approves in the browser. */
    @POST("api/miauth/{session}/check")
    suspend fun miAuthCheck(@Path("session") session: String): MiAuthCheckDto

    @POST("api/i")
    suspend fun currentUser(@Body request: CredentialRequest): MisskeyUserDto

    /** Home timeline. Note: reaches back roughly 30 days only (misskey-dev/misskey#10063). */
    @POST("api/notes/timeline")
    suspend fun homeTimeline(@Body request: TimelineRequest): List<NoteDto>

    @POST("api/notes/hybrid-timeline")
    suspend fun socialTimeline(@Body request: TimelineRequest): List<NoteDto>

    /**
     * Marks every notification read. This is NOT a timeline read marker — Misskey has no
     * equivalent of Mastodon's `/api/v1/markers` — and it is exposed here only so the
     * notifications badge can be cleared honestly.
     */
    @POST("api/notifications/mark-all-as-read")
    suspend fun markAllNotificationsRead(@Body request: NotificationsReadRequest): Response<Unit>

    /**
     * Marks notes that mention the user, or are addressed to them, as read. Covers a
     * strict subset of the timeline: an ordinary followed post is not affected.
     */
    @POST("api/i/read-all-unread-notes")
    suspend fun readAllUnreadNotes(@Body request: NotificationsReadRequest): Response<Unit>

    @POST("api/users/show")
    suspend fun userShow(@Body request: UserShowRequest): MisskeyUserDto

    /** Recent notes by one user, used by the in-app profile view. */
    @POST("api/users/notes")
    suspend fun userNotes(@Body request: UserNotesRequest): List<NoteDto>

    /**
     * Accounts the user follows. Misskey wraps each entry, so the followee sits one level
     * down rather than being the list element itself.
     */
    @POST("api/users/following")
    suspend fun following(@Body request: FollowingRequest): List<FollowingDto>

    @POST("api/antennas/list")
    suspend fun antennas(@Body request: CredentialRequest): List<AntennaDto>

    /**
     * The one path where Misskey does carry a server-side unread flag: an antenna exposes
     * `hasUnreadNote`, and fetching its notes with `markAsRead` clears it.
     */
    @POST("api/antennas/notes")
    suspend fun antennaNotes(@Body request: AntennaNotesRequest): List<NoteDto>
}
