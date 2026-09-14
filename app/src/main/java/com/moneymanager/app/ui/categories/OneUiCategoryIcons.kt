package com.moneymanager.app.ui.categories

import androidx.compose.ui.graphics.Color

/**
 * A purpose-built category icon vocabulary. These are NOT Material default icons.
 * Each category resolves to a small custom geometric glyph rendered by CategoryGlyph,
 * while CategoryVisual supplies the shared One UI-inspired tonal squircle/depth.
 */
enum class CategoryIconKind {
    Transfer, CreditCard, Gift, Interest, InvestmentIncome, Loan, MutualFunds, Other,
    ProvidentFund, Refund, Reward, Salary, Savings, Selling, Upi, WalletRecharge,
    Bills, Dinner, Donation, Finance, Gas, Household, Kids, Maintenance, Medical,
    Miscellaneous, Shopping, Transport, Travel, Grocery, Clothes, Beauty, Education,
    Mobile, Cash, Electricity, Water, Wifi, AirTicket, Bike, Car, Bus, Courier,
    Pets, Music, Movie, Nightlife, Sports, Coffee, Food, Books, Breakfast, Cigarette,
    Insurance, Investments, RentMortgage, Home, Family, Electronics, Entertainment,
    DailyCare, Fitness, Cycle, Dining, FastFood, Repair, Parking, Fuel, Pharmacy,
    Subscription, Tax, SalaryBonus, DiningOut, Charity, PhoneBill, Broadband,
    Delivery, HouseholdGoods, PersonalCare, BabyCare, Toys, TravelHotel, OtherFinance,
    Custom
}

data class OneUiCategoryStyle(
    val kind: CategoryIconKind,
    val baseColor: Color
)

private fun getCategoryBaseColor(name: String, key: String): Color {
    val n = "$name $key".lowercase()
    return when {
        "income" in n || "salary" in n || "refund" in n || "reward" in n || "saving" in n -> Color(0xFF22C55E)
        "bill" in n || "utility" in n || "electric" in n || "tax" in n || "water" in n -> Color(0xFFF59E0B)
        "food" in n || "dinner" in n || "breakfast" in n || "coffee" in n || "dining" in n -> Color(0xFFF97316)
        "grocery" in n || "vegetable" in n || "fruit" in n || "market" in n -> Color(0xFF20B26B)
        "transport" in n || "car" in n || "bus" in n || "bike" in n || "cycle" in n || "fuel" in n -> Color(0xFF3B82F6)
        "health" in n || "medical" in n || "pharma" in n || "care" in n -> Color(0xFF17BFA3)
        "education" in n || "book" in n || "school" in n || "course" in n -> Color(0xFFF5B82E)
        "entertainment" in n || "movie" in n || "music" in n || "game" in n || "disco" in n -> Color(0xFFA855F7)
        "travel" in n || "hotel" in n || "flight" in n || "air ticket" in n -> Color(0xFF06B6D4)
        "home" in n || "house" in n || "rent" in n || "mortgage" in n -> Color(0xFF9A6B53)
        "shopping" in n || "gift" in n || "cloth" in n || "beauty" in n -> Color(0xFFEC5B8C)
        "loan" in n || "emi" in n || "finance" in n || "bank" in n -> Color(0xFF6366F1)
        "insurance" in n || "security" in n -> Color(0xFF3B82F6)
        "wallet" in n || "cash" in n || "upi" in n -> Color(0xFF10B981)
        "family" in n || "kids" in n || "baby" in n || "pet" in n -> Color(0xFF14B8A6)
        "business" in n || "work" in n || "office" in n -> Color(0xFF64748B)
        else -> Color(0xFF64748B)
    }
}

/** Maps the complete known category vocabulary to a purpose-built glyph. */
fun oneUiCategoryStyle(name: String, key: String = "", isTransfer: Boolean = false): OneUiCategoryStyle {
    val n = "$name $key".lowercase().replace("-", " ").trim()
    val color = getCategoryBaseColor(name, key)

    val kind = when {
        isTransfer || "a/c to a/c" in n || "transfer" in n -> CategoryIconKind.Transfer
        "cc bill payment" in n || "credit card" in n || "debit card" in n -> CategoryIconKind.CreditCard
        "wallet recharge" in n || "prepaid wallet" in n -> CategoryIconKind.WalletRecharge
        "upi" in n -> CategoryIconKind.Upi
        "provident fund" in n -> CategoryIconKind.ProvidentFund
        "investment income" in n -> CategoryIconKind.InvestmentIncome
        "mutual fund" in n -> CategoryIconKind.MutualFunds
        "stocks selling" in n || "stocks buying" in n -> CategoryIconKind.Selling
        "investment" in n || "stock" in n || "stocks" in n || "sip" in n || "dividend" in n -> CategoryIconKind.Investments
        "salary bonus" in n || "bonus" in n -> CategoryIconKind.SalaryBonus
        "salary" in n -> CategoryIconKind.Salary
        "interest" in n -> CategoryIconKind.Interest
        "refund" in n -> CategoryIconKind.Refund
        "reward" in n -> CategoryIconKind.Reward
        "saving" in n -> CategoryIconKind.Savings
        "selling" in n || "sale" in n -> CategoryIconKind.Selling
        "loan" in n || "emi" in n || "repayment" in n -> CategoryIconKind.Loan
        n == "finance" || "finance" in n && "loan" !in n && "emi" !in n -> CategoryIconKind.Finance
        "gift" in n || "flowers" in n -> CategoryIconKind.Gift
        "donation" in n || "charity" in n || "temple" in n || "pooja" in n -> CategoryIconKind.Charity
        "bill" in n || "utility" in n || "subscription" in n -> CategoryIconKind.Bills
        "electric" in n || "power" in n -> CategoryIconKind.Electricity
        "water" in n -> CategoryIconKind.Water
        "wifi" in n || "broadband" in n || "internet" in n || "wi fi" in n -> CategoryIconKind.Broadband
        "phone bill" in n || "mobile bill" in n -> CategoryIconKind.PhoneBill
        "mobile" in n || "phone" in n || "recharge" in n -> CategoryIconKind.Mobile
        "air ticket" in n || "flight" in n -> CategoryIconKind.AirTicket
        "travel hotel" in n || "hotel" in n -> CategoryIconKind.TravelHotel
        "luggage" in n || "travel" in n || "trip" in n || "vacation" in n -> CategoryIconKind.Travel
        "bus" in n -> CategoryIconKind.Bus
        "transport" in n || "auto" in n || "taxi" in n || "cab" in n -> CategoryIconKind.Transport
        "bike" in n || "scooter" in n || "two wheeler" in n -> CategoryIconKind.Bike
        "cycle" in n -> CategoryIconKind.Cycle
        "car" in n -> CategoryIconKind.Car
        "parking" in n || "toll" in n -> CategoryIconKind.Parking
        "fuel" in n || "petrol" in n || "diesel" in n || "gas" in n -> CategoryIconKind.Fuel
        "courier" in n || "shipping" in n || "parcel" in n || "post office" in n -> CategoryIconKind.Courier
        "delivery" in n -> CategoryIconKind.Delivery
        "grocery" in n || "supermarket" in n || "vegetable" in n || "vegetables" in n || "fruit" in n || "fruits" in n || "kirana" in n || "market" in n -> CategoryIconKind.Grocery
        "fast food" in n -> CategoryIconKind.FastFood
        "food" in n || "cookie" in n || "cookies" in n || "ice cream" in n || "pastries" in n || "sweet and snacks" in n -> CategoryIconKind.Food
        "dinner" in n || "lunch" in n -> CategoryIconKind.Dinner
        "breakfast" in n -> CategoryIconKind.Breakfast
        "dining" in n || "restaurant" in n || "meal" in n -> CategoryIconKind.DiningOut
        "coffee" in n || "tea" in n || "juice" in n -> CategoryIconKind.Coffee
        "book" in n || "novel" in n || "stationery" in n || "printing/scanning" in n -> CategoryIconKind.Books
        "education" in n || "school" in n || "college" in n || "tuition" in n -> CategoryIconKind.Education
        "cloth" in n || "dress" in n || "fashion" in n || "apparel" in n || "shoes" in n -> CategoryIconKind.Clothes
        "shopping" in n || "mall" in n || "store" in n || "jewellery" in n || "jewelry" in n -> CategoryIconKind.Shopping
        "beauty" in n || "spa" in n || "salon" in n || "cosmetic" in n -> CategoryIconKind.Beauty
        "fitness" in n || "gym" in n || "yoga" in n -> CategoryIconKind.Fitness
        "health" in n || "medical" in n || "hospital" in n || "doctor" in n -> CategoryIconKind.Medical
        "pharma" in n || "medicine" in n || "drug" in n -> CategoryIconKind.Pharmacy
        "insurance" in n -> CategoryIconKind.Insurance
        "entertainment" in n || "game" in n || "ott" in n || "toys" in n -> CategoryIconKind.Entertainment
        "movie" in n || "cinema" in n || "netflix" in n -> CategoryIconKind.Movie
        "music" in n || "song" in n || "concert" in n -> CategoryIconKind.Music
        "disco" in n || "nightlife" in n || "party" in n || "pub" in n || "bar" in n -> CategoryIconKind.Nightlife
        "sports" in n || "cricket" in n || "football" in n -> CategoryIconKind.Sports
        "pet" in n || "animal" in n || "dog" in n || "cat" in n -> CategoryIconKind.Pets
        "kids" in n || "child" in n -> CategoryIconKind.Kids
        "baby" in n || "infant" in n -> CategoryIconKind.BabyCare
        "daily care" in n || "personal care" in n || "self" in n -> CategoryIconKind.PersonalCare
        "household goods" in n || "cooking oil" in n -> CategoryIconKind.HouseholdGoods
        "household" in n -> CategoryIconKind.Household
        "rent" in n || "mortgage" in n -> CategoryIconKind.RentMortgage
        "home" in n || "house" in n -> CategoryIconKind.Home
        "family" in n -> CategoryIconKind.Family
        "maintenance" in n || "repair" in n || "service" in n -> CategoryIconKind.Maintenance
        "electronics" in n || "electronic" in n || "device" in n || "laptop" in n -> CategoryIconKind.Electronics
        "cigarette" in n || "smoke" in n || "tobacco" in n -> CategoryIconKind.Cigarette
        "tax" in n || "gst" in n -> CategoryIconKind.Tax
        "cash forward" in n || "cash withdrawal" in n || "cash spend" in n || "cash" in n || "atm" in n -> CategoryIconKind.Cash
        "gold" in n -> CategoryIconKind.Investments
        "drinks" in n -> CategoryIconKind.Coffee
        "canara bank" in n || "cheque" in n -> CategoryIconKind.Finance
        "office" in n || "business" in n || "work" in n || "printing/scanning" in n -> CategoryIconKind.OtherFinance
        "imps" in n || "online transfer" in n || "netbanking" in n || "online banking" in n -> CategoryIconKind.Transfer
        "miscellaneous" in n -> CategoryIconKind.Miscellaneous
        "others" in n || "unknown" in n || "unknown sender" in n -> CategoryIconKind.Other
        "custom" in n -> CategoryIconKind.Custom
        else -> CategoryIconKind.Custom
    }
    return OneUiCategoryStyle(kind = kind, baseColor = color)
}
