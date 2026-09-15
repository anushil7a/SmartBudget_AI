package com.example.project3_aadhika8_sguragai.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "budget")
public class Budget {

    @PrimaryKey
    public int id = 1;

    public double amount;
}


