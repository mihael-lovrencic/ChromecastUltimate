package com.example.castultimate

object Versioning {

    fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").mapNotNull { parsePart(it) }
        val rightParts = right.split(".").mapNotNull { parsePart(it) }

        for (i in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrElse(i) { 0 }
            val rightPart = rightParts.getOrElse(i) { 0 }

            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    private fun parsePart(part: String): Int? {
        val digits = part.takeWhile { it.isDigit() }
        return digits.toIntOrNull()
    }
}
