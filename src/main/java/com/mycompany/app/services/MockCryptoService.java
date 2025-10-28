package com.mycompany.app.services;

import java.util.ArrayList;
import java.util.List;

import com.mycompany.app.models.Crypto;

public class MockCryptoService implements CryptoService {
    @Override
    public List<Crypto> getTopCryptos() {
        List<Crypto> list = new ArrayList<>();
        list.add(new Crypto("xrp", "XRP", "XRP", 6.00, 100, "$33.0B", "$800M", "50.0B"));
        list.add(new Crypto("btc", "Bitcoin", "BTC", 63421.00, 3.5, "$1.2T", "$32B", "19.0M"));
        list.add(new Crypto("eth", "Ethereum", "ETH", 3456.78, 2.37, "$415.6B", "$20B", "118.0M"));
        list.add(new Crypto("sol", "Solana", "SOL", 178.90, 3.58, "$35.8B", "$2.5B", "300.0M"));
        list.add(new Crypto("ada", "Cardano", "ADA", 2.34, 1.96, "$45.6B", "$1.8B", "32.0B"));
        list.add(new Crypto("bnb", "Binance Coin", "BNB", 456.78, 0.91, "$75.3B", "$1.5B", "168.0M"));
        return list;
    }
}
