package com.lechixy.kick.data.account.local

interface SessionStore {
    fun read(name: String): String?
    fun write(name: String, value: String?)
}
