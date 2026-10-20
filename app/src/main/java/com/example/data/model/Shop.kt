package com.example.data.model

data class CatalogItem(
  val id: String,
  val name: String,
  val priceFormatted: String, // e.g. "₹899"
  val description: String,
  val imageUrl: String,
  val inStock: Boolean = true
)

data class ShopReview(
  val id: String,
  val author: User,
  val ratingStars: Int, // 1 to 5
  val reviewText: String,
  val timestamp: String,
  val isVerifiedCustomer: Boolean = true,
  val ownerReply: String? = null
)

data class VybeShop(
  val id: String,
  val businessName: String,
  val category: String, // e.g. "Artisan Coffee & Bakery", "Sustainable Streetwear", "Indie Books & Records"
  val description: String,
  val logoUrl: String,
  val coverUrl: String,
  val rating: Float = 4.8f,
  val reviewsCount: Int = 128,
  val isVerifiedBusiness: Boolean = true,
  val openingHours: String = "10:00 AM - 10:00 PM",
  val locationAddress: String = "Khar Social Lane, Mumbai",
  val contactPhone: String = "+91 98200 12345",
  val contactEmail: String = "contact@shop.vybe.in",
  val catalog: List<CatalogItem> = emptyList(),
  val reviews: List<ShopReview> = emptyList(),
  val followersCount: Int = 2450,
  val isFollowedByMe: Boolean = false
)
