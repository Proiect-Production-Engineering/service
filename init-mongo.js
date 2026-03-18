// Usage: mongosh "mongodb://root:example@localhost:27017/prod-eng?authSource=admin" init-mongo.js

db = db.getSiblingDB("prod-eng");

print("Initializing currencies...");
db.currencies.drop();
db.currencies.insertMany([
    { name: "Euro", code: "EUR" },
    { name: "Romanian new leu", code: "RON" },
    { name: "British pound sterling", code: "GBP" }
]);
db.currencies.createIndex({ code: 1 }, { unique: true });
print("Currencies: " + db.currencies.countDocuments() + " inserted.");

print("Initializing countries...");
db.countries.drop();
db.countries.insertMany([
    { name: "Romania",        code: "RO", ibanPattern: "aaaacccccccccccccccc" },
    { name: "France",         code: "FR", ibanPattern: "nnnnnnnnnncccccccccccnn" },
    { name: "Germany",        code: "DE", ibanPattern: "nnnnnnnnnnnnnnnnnn" },
    { name: "Italy",          code: "IT", ibanPattern: "annnnnnnnnncccccccccccc" },
    { name: "United Kingdom", code: "GB", ibanPattern: "aaaannnnnnnnnnnnnn" }
]);
db.countries.createIndex({ code: 1 }, { unique: true });
print("Countries: " + db.countries.countDocuments() + " inserted.");

print("Initializing exchange rates...");
db.currency_exchange_rates.drop();
db.currency_exchange_rates.insertMany([
    { sourceCurrency: "EUR", targetCurrency: "RON", exchangeRate: 5.0607 },
    { sourceCurrency: "EUR", targetCurrency: "GBP", exchangeRate: 0.8432 },
    { sourceCurrency: "RON", targetCurrency: "EUR", exchangeRate: 0.1975 },
    { sourceCurrency: "RON", targetCurrency: "GBP", exchangeRate: 0.1665 },
    { sourceCurrency: "GBP", targetCurrency: "EUR", exchangeRate: 1.1860 },
    { sourceCurrency: "GBP", targetCurrency: "RON", exchangeRate: 6.0027 }
]);
print("Exchange rates: " + db.currency_exchange_rates.countDocuments() + " inserted.");

print("Ensuring bank_accounts indexes...");
db.bank_accounts.createIndex({ iban: 1 }, { unique: true });
db.bank_accounts.createIndex({ userId: 1 });

print("Looking up admin user...");
var adminUser = db.users.findOne({ username: "admin" });
if (adminUser) {
    var adminId = adminUser._id.toString();
    if (!db.bank_accounts.findOne({ iban: "RO83OPPCo1JNAQ8eEheih5zI" })) {
        var accountInsertResult = db.bank_accounts.insertOne({
            iban: "RO83OPPCo1JNAQ8eEheih5zI",
            userId: adminId,
            currencyCode: "EUR",
            countryCode: "RO",
            accountHolderName: "Admin",
            balance: 1000000.0,
            deleted: false
        });
        var adminAccountId = accountInsertResult.insertedId.toString();
        db.transactions.insertOne({
            accountId: adminAccountId,
            type: "CREDIT",
            amount: NumberDecimal("1000000.00"),
            description: "Initial seed balance",
            timestamp: new Date()
        });
        print("Admin bank account created with 1,000,000 EUR and initial CREDIT transaction (userId: " + adminId + ").");
    } else {
        print("Admin bank account already exists, skipping.");
    }
} else {
    print("WARNING: Admin user not found. Start the Spring Boot app first, then re-run this script.");
}

print("\nDone! Database 'prod-eng' initialized successfully.");
