package dropgate.order.controller

import dropgate.common.authentication.AuthenticatedUser
import dropgate.order.service.DropResponse
import dropgate.order.service.DropService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class DropController(private val drops: DropService) {
    @PostMapping("/api/admin/drops")
    fun createDrop(admin: AuthenticatedUser, @RequestBody @Valid request: CreateDropRequest): ResponseEntity<DropResponse> {
        val drop = drops.createDrop(request.toCommand(), admin.id)
        return ResponseEntity.created(URI.create("/api/drops/${drop.id}")).body(drop)
    }

    @GetMapping("/api/drops/{dropId}")
    fun getDrop(@PathVariable("dropId") dropId: UUID): DropResponse = drops.getDrop(dropId)
}
