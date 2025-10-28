package com.mycompany.app.service;

import com.mycompany.app.model.Crypto;
import java.util.ArrayList;
import java.util.List;

public class MockCryptoService implements CryptoService {
    @Override
    public List<Crypto> getTopCryptos() {
        List<Crypto> list = new ArrayList<>();
        list.add(new Crypto("btc", "Bitcoin", "BTC", 60123.45, 2.5, "$1.12T"));
        list.add(new Crypto("eth", "Ethereum", "ETH", 3456.78, -1.2, "$415.6B"));
        list.add(new Crypto("bnb", "Binance Coin", "BNB", 456.78, 0.8, "$75.3B"));
        list.add(new Crypto("ada", "Cardano", "ADA", 2.34, 5.6, "$45.6B"));
        list.add(new Crypto("sol", "Solana", "SOL", 178.90, -0.7, "$35.8B"));
        return list;
    }
}
