# Reproduce a race-condition in WebappClassLoader

`ReproduceTest.shouldProvokeRaceConditionIn_findResourceInternalFromJars`
concurrently calls `WebappClassLoader.addJar()`, `WebappClassLoader.findResource()` and `WebappClassLoader.closeJARs()`.

The exceptions thrown by `WebappClassLoader.findResource()` (written to the console / `System.out`) show 
that ``.findResource()` and `WebappClassLoader.closeJARs()` are not properly synchronized with each other.

*Please note:* The occurrence of this problem strongly depends on the threads timings and thus on the executing machine. 
Basically, this works on my machine; you might have to change the timings by playing around with the `Thread.sleep()` 
intervals to provoke the issue.

## Proposed Solution
`WebappClassLoader.closeJARs()` (and some other methods) synchronizes on the field `WebappClassLoader.jarFilesLock`.
`WebappClassLoader.findResource()`'s helper method `findClassInternal()` synchronizes on `WebappClassLoader.jarFiles` and is the only method that does that.

I think it's very likely that it was meant to synchronize on `WebappClassLoader.jarFilesLock`, too.

Additionally IntelliJ warns:

> Synchronization on a non-final field 'jarFiles'
> Reports synchronized statements where the lock expression is a reference to a non-final field. Such statements are unlikely to have useful semantics, as different threads may be locking on different objects even when operating on the same object. 
