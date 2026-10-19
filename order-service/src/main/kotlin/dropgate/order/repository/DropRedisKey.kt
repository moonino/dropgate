package dropgate.order.repository

import java.util.UUID

internal fun createDropRedisKey(dropId: UUID, suffix: String): String = "drop:$dropId:$suffix"
