#include <iostream>
#include <dlfcn.h>

#include "types.h"

typedef Point2D (*fp)(Point2D);

int main(int argc, const char * argv[]) {

    using std::cout;
    void * handle = NULL;
    handle = dlopen("/Users/chengenzhao/Desktop/libHelloSwift.dylib", RTLD_GLOBAL);
    if(handle == NULL){
        cout<<"can't load the lib"<<dlerror();
        return 1;
    }
    fp op;
    op = (fp)dlsym(handle, "test");

    Point2D p;
    p.x = 1;
    p.y = 2;

    cout<<"Swaped x is "<<op(p).x<<std::endl;

    return 0;
}