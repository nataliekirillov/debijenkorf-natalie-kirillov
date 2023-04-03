package com.debijenkorf.assignment.strategy;

public interface DirectoryStrategy {
    /**
     * Get directory strategy for specific cloud storage implementation
     *
     * @param typeName Definition type
     * @param filename The file we would like to save
     * @return A filepath to store the file in
     */
    String getDirectoryStrategy(String typeName, String filename);
}
