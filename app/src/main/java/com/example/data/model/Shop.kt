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
  val photoUrl: String? = null,
  val isModerated: Boolean = true,
  val ownerReply: String? = null
)

enum class BusinessVerificationStatus {
  NOT_APPLIED, PENDING, UNDER_REVIEW, APPROVED, REJECTED, MORE_INFO_REQUIRED
}

data class BusinessApplication(
  val id: String,
  val ownerId: String,
  val businessName: String,
  val category: String,
  val registrationNumberOrGst: String,
  val contactEmail: String,
  val contactPhone: String,
  val address: String,
  val status: BusinessVerificationStatus = BusinessVerificationStatus.PENDING,
  val submittedAt: String,
  val reviewerNotes: String? = null,
  val isActivated: Boolean = false
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
  val isFollowedByMe: Boolean = false,
  val ownerId: String = "u_shop_owner",
  val verificationStatus: BusinessVerificationStatus = BusinessVerificationStatus.APPROVED,
  val isShopActive: Boolean = true
)
