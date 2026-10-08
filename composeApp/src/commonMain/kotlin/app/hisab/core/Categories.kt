package app.hisab.core

data class Category(val id: String, val emoji: String, val label: String, val keywords: List<String>)

object Categories {
    val all = listOf(
        Category("food", "🍽️", "Food", listOf("dinner", "lunch", "breakfast", "restaurant", "cafe", "coffee", "momo", "pizza", "khana", "snack", "tea", "chiya")),
        Category("groceries", "🛒", "Groceries", listOf("grocery", "groceries", "vegetables", "veg", "milk", "bhatbhateni", "market", "supermarket", "fruit", "rice")),
        Category("rent", "🏠", "Rent", listOf("rent", "bhada", "deposit", "landlord")),
        Category("utilities", "💡", "Bills", listOf("electricity", "water", "wifi", "internet", "gas", "bill", "phone", "recharge", "nea")),
        Category("transport", "🚕", "Transport", listOf("taxi", "uber", "pathao", "indrive", "bus", "fuel", "petrol", "parking", "train", "metro")),
        Category("travel", "✈️", "Travel", listOf("flight", "hotel", "ticket", "trip", "airbnb", "hostel", "visa")),
        Category("fun", "🎉", "Fun", listOf("movie", "party", "drinks", "beer", "game", "concert", "netflix", "spotify")),
        Category("shopping", "🛍️", "Shopping", listOf("clothes", "shoes", "gift", "amazon", "daraz", "shopping")),
        Category("health", "💊", "Health", listOf("doctor", "medicine", "pharmacy", "hospital", "gym")),
        Category("other", "📦", "Other", emptyList()),
    )

    fun byId(id: String): Category = all.firstOrNull { it.id == id } ?: all.last()

    /** Guesses a category from what was typed ("Dinner at Thamel" -> Food). */
    fun guess(description: String): Category? {
        val words = description.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return all.firstOrNull { c -> c.keywords.any { k -> words.any { w -> w == k || w.startsWith(k) } } }
    }
}
