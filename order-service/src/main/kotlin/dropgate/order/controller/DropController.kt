package dropgate.order.controller

import dropgate.common.authentication.AuthenticatedUser
import dropgate.order.dto.CreateDropRequest
import dropgate.order.dto.DropPageResponse
import dropgate.order.dto.DropResponse
import dropgate.order.repository.RemainingStock
import dropgate.order.service.DropCursor
import dropgate.order.service.DropService
import dropgate.order.service.DropWithStock
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

private const val DEFAULT_PAGE_LIMIT = "20"
private const val MIN_PAGE_LIMIT = 1L
private const val MAX_PAGE_LIMIT = 100L

@RestController
class DropController(private val drops: DropService) {
    @PostMapping("/api/admin/drops")
    fun createDrop(admin: AuthenticatedUser, @RequestBody @Valid request: CreateDropRequest): ResponseEntity<DropResponse> {
        val drop = drops.createDrop(request.toCommand(), admin.id)
        return ResponseEntity.created(URI.create("/api/drops/${drop.id}")).body(DropResponse.createWithoutStock(drop))
    }

    @GetMapping("/api/drops")
    fun getDrops(
        @RequestParam("cursor", required = false) cursor: String?,
        @RequestParam("limit", defaultValue = DEFAULT_PAGE_LIMIT)
        @Min(MIN_PAGE_LIMIT, message = "limit은 {value} 이상이어야 합니다")
        @Max(MAX_PAGE_LIMIT, message = "limit은 {value} 이하여야 합니다")
        limit: Int,
    ): DropPageResponse {
        val page = if (cursor == null) drops.getFirstDrops(limit) else drops.getDropsAfter(DropCursor.decode(cursor), limit)
        return DropPageResponse(page.items.map(::toResponse), page.nextCursor)
    }

    @GetMapping("/api/drops/{dropId}")
    fun getDrop(@PathVariable("dropId") dropId: UUID): DropResponse = toResponse(drops.getDrop(dropId))
    private fun toResponse(dropWithStock: DropWithStock): DropResponse = DropResponse.create(
        dropWithStock.drop,
        when (val stock = dropWithStock.stock) {
            is RemainingStock.Known -> stock.quantity
            RemainingStock.Unknown -> null
        },
    )
}
