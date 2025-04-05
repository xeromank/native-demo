package com.example.nativedemo.service

import com.example.nativedemo.dto.UserDTO
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class UserServiceImpl : UserService {
    private val users = ConcurrentHashMap<Long, UserDTO>()

    init {
        // 샘플 데이터 추가
        users[1] = UserDTO(1, "John Doe", "john@example.com", listOf("USER"))
        users[2] = UserDTO(2, "Jane Smith", "jane@example.com", listOf("USER", "ADMIN"))
    }

    override fun getUsers(): List<UserDTO> = users.values.toList()

    override fun getUserById(id: Long): UserDTO? = users[id]

    override fun createUser(user: UserDTO): UserDTO {
        users[user.id] = user
        return user
    }
}
