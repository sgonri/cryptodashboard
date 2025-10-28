package com.mycompany.app.services;

import java.util.List;

import com.mycompany.app.models.News;

public interface NewsService {
    List<News> getNewsForCrypto(String cryptoId);
    List<News> getGeneralNews();
}
