package com.example.aichatbot.auth

import com.example.aichatbot.user.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class CurrentUser(
    val id: Long,
    val email: String,
    val role: Role,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String? = null

    override fun getUsername(): String = email
}
