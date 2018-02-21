/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

/**
 *
 * @author teofil
 */
public class CollectionUtil {
    private CollectionUtil(){}
    
    public static boolean inArray(int element, int [] array) {
        for(int cmp : array) {
            if(cmp == element) return true;
        }
        return false;
    }
}
